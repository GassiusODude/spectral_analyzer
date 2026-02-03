package net.kcundercover.spectral_analyzer;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.stage.Stage;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.plot.ValueMarker;
import javafx.scene.input.MouseEvent; // For the event trigger
import org.jfree.chart.title.TextTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.renderer.xy.SamplingXYLineRenderer;

import net.kcundercover.jdsp.signal.PowerSpectralDensity;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;

public class AnalysisDialogController {
    private static final Logger ADC_LOGGER = LoggerFactory.getLogger(AnalysisDialogController.class);

    /** Active annotation is a copy of the annotation,
     * introduced through setAnalysisData()
     */
    private SigMfAnnotation activeAnnotation;

    // variables for displaying and tracking user defined passband/noisefloor characteristics
    // ------------------------------------------------------------------------
    private ValueMarker passbandMarker;
    private ValueMarker noiseFloorMarker;
    private double currentPassbandLevel = 0;
    private double currentNoiseFloor = 0;

    // selection of frequency range in PSD
    // ------------------------------------------------------------------------
    private double lowCutoff = 0;
    private double highCutoff = 0;
    private Double firstFreqClick = null; // Member variable


    // @FXML private ScatterChart<Number, Number> constellationChart;
    @FXML private StackPane containerMagn;
    @FXML private StackPane containerFreq;
    @FXML private StackPane containerPSD;

    // section for displaying time/freq/label/comment from annotation
    // ------------------------------------------------------------------------
    @FXML private TextField txtAnnotLabel, txtAnnotTime, txtAnnotFreq;
    @FXML private TextArea txtAnnotComment;
    @FXML private Button btnUpdateFreq;
    @FXML private CheckBox chkIncludeMetrics;

    private ChartViewer viewMagn;
    private ChartViewer viewFreq;
    private ChartViewer viewPSD;
    private XYSeries seriesMagn;
    private XYSeries seriesFreq;
    private XYSeries seriesPSD;

    /**
     * Configure charts to use JFreeChart to handle large input data sizes
     */
    public void initialize() {
        // MAGNITUDE: Wiring only
        seriesMagn = new XYSeries("Magnitude");
        viewMagn = createBaseChart(containerMagn, "Magnitude of Segment", new XYSeriesCollection(seriesMagn));

        // FREQUENCY: Wiring only
        seriesFreq = new XYSeries("Frequency");
        viewFreq = createBaseChart(containerFreq, "Instantaneous Frequency", new XYSeriesCollection(seriesFreq));

        // PSD: Wiring + Interaction
        seriesPSD = new XYSeries("PSD");
        viewPSD = createBaseChart(containerPSD, "Power Spectral Density", new XYSeriesCollection(seriesPSD));


        // support user interaction to specify passband and noisefloor, and frequency range
        viewPSD.addChartMouseListener(new ChartMouseListenerFX() {
            @Override
            public void chartMouseClicked(ChartMouseEventFX event) {
                MouseEvent mouseEvent = event.getTrigger();

                // Translate pixel coordinates to chart data values
                java.awt.geom.Rectangle2D dataArea = viewPSD.getCanvas().getRenderingInfo().getPlotInfo().getDataArea();
                XYPlot plot = (XYPlot) viewPSD.getChart().getPlot();

                double xValue = plot.getDomainAxis().java2DToValue(
                    mouseEvent.getX(), dataArea, plot.getDomainAxisEdge());
                double yValue = plot.getRangeAxis().java2DToValue(
                    mouseEvent.getY(), dataArea, plot.getRangeAxisEdge());

                handlePsdInteraction(event.getTrigger(), xValue, yValue);
            }

            @Override
            public void chartMouseMoved(ChartMouseEventFX event) {}
        });
    }

    /**
     * Helper to configure charts using JFreeChart rendering to support
     * large input data.
     */
    private ChartViewer createBaseChart(StackPane container, String title, XYSeriesCollection dataset) {
        JFreeChart chart = ChartFactory.createXYLineChart(title, "", "", dataset);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(new SamplingXYLineRenderer());

        ChartViewer viewer = new ChartViewer(chart);
        container.getChildren().add(viewer);
        return viewer;
    }

    /**
     * Reset the axes of the given plot
     * @param viewer viewer
     * @param xLabel Label for the x-axis
     * @param yLabel Label for the y-axis
     */
    private void resetAxes(ChartViewer viewer, String xLabel, String yLabel) {
        XYPlot plot = viewer.getChart().getXYPlot();
        plot.getDomainAxis().setLabel(xLabel);
        plot.getRangeAxis().setLabel(yLabel);
        plot.getDomainAxis().setAutoRange(true);
        plot.getRangeAxis().setAutoRange(true);
    }

    /**
     * Update the magnitude chart
     *
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     */
    private void updateMagnitudeChart(double[][] data, double sampleRate) {
        seriesMagn.clear();
        double timeStep = 1.0 / sampleRate;
        for (int i = 0; i < data[0].length; i++) {
            double logMag = 20 * Math.log10(Math.hypot(data[0][i], data[1][i]));
            if (Double.isFinite(logMag)) {
                seriesMagn.add(i * timeStep, logMag, false);
            }
        }
        seriesMagn.fireSeriesChanged();
        resetAxes(viewMagn, "Time (s)", "Magnitude (dB)");
    }

    /**
     * Update the frequency chart
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     */
    private void updateFrequencyChart(double[][] data, double sampleRate) {
        seriesFreq.clear();
        double timeStep = 1.0 / sampleRate;
        for (int i = 1; i < data[0].length; i++) {
            double phase1 = Math.atan2(data[1][i], data[0][i]);
            double phase2 = Math.atan2(data[1][i - 1], data[0][i - 1]);

            double dPhase = phase1 - phase2;
            // Wrap phase to [-PI, PI]
            if (dPhase > Math.PI) {
                dPhase -= 2 * Math.PI;
            } else if (dPhase < -Math.PI) {
                dPhase += 2 * Math.PI;
            }

            double instFreq = (dPhase / (2 * Math.PI)) * sampleRate;
            seriesFreq.add(i * timeStep, instFreq, false);
        }
        seriesFreq.fireSeriesChanged();
        resetAxes(viewFreq, "Time (s)", "Frequency (Hz)");
    }

    /**
     * Update the Power Spectral Density chart
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     */
    private void updatePSDChart(double[][] data, double sampleRate) {
        seriesPSD.clear();
        // Implementation requires an FFT library like JTransforms
        // 1. Combine data[0] and data[1] into complex array
        int psdNfft = 8192;
        double offset;
        double[][] freqAndPsd;
        if (data[0].length < psdNfft) {
            ADC_LOGGER.warn("Short signal in PSD, single window analysis, and different NFFT");
            freqAndPsd = PowerSpectralDensity.calculatePsdWelch(
                data, sampleRate, data[0].length);
        } else {
            freqAndPsd = PowerSpectralDensity.calculatePsdWelch(
                data, sampleRate, psdNfft);
        }

        // FIXME: (not working)
        //        Attempt to compare against threshold used in spectrogram)
        // offset = 10 * Math.log10(sampleRate / freqAndPsd[0].length);
        offset = 0;

        // get the true center from the provided annotation
        double trueCenterFreq = 0.5 * (activeAnnotation.getFreqLowerEdge() + activeAnnotation.getFreqUpperEdge());

        for (int ind = 0; ind < freqAndPsd[0].length; ind++) {
            seriesPSD.add(
                freqAndPsd[0][ind] + trueCenterFreq,
                freqAndPsd[1][ind] + offset, false);
        }
        seriesPSD.fireSeriesChanged();
        resetAxes(viewPSD, "Frequency (Hz)", "Power/Hz");
    }

    /**
     * Get updated annotation
     *
     *
     * @return Return a copy of the updated annotation
     */
    public SigMfAnnotation getUpdatedAnnotation() {
        return new SigMfAnnotation(this.activeAnnotation);
    }

    /**
     * Formats time values into human-readable units (s, ms, μs).
     * @param seconds The time value in seconds.
     * @return Formatted string (e.g., "1.23 s", "45.00 ms", "12.50 μs")
     */
    public String formatTime(double seconds) {
        if (seconds >= 1.0) {
            return String.format("%.2f s", seconds);
        } else if (seconds >= 1e-3) {
            return String.format("%.2f ms", seconds * 1e3);
        } else if (seconds >= 1e-6) {
            // Use the UTF-8 Greek mu symbol
            return String.format("%.2f μs", seconds * 1e6);
        } else {
            // Fallback for extremely small values (nanoseconds)
            return String.format("%.2f ns", seconds * 1e9);
        }
    }


    /**
     * The entry point to this dialog from the main application
     *
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     * @param origSampleRate the original sampling rate of the signal
     * @param newAnnot The current SigMFAnnotation
     */
    public void setAnalysisData(double[][] data, double sampleRate, double origSampleRate, SigMfAnnotation newAnnot) {
        if (data == null || data.length < 2) {
            return;
        }

        // -----------------------  load annotation ------------------------
        // save a copy of the annotation (potentially updated to be returned)
        activeAnnotation = new SigMfAnnotation();
        activeAnnotation.copy(newAnnot);

        txtAnnotLabel.setText(newAnnot.getLabel());
        txtAnnotComment.setText(newAnnot.getComment());
        txtAnnotTime.setText(
            String.format("Start at %s (duration = %s)",
                formatTime(activeAnnotation.getSampleStart() / origSampleRate),
                formatTime(activeAnnotation.getSampleCount() / origSampleRate)));
        txtAnnotFreq.setText(
            String.format("Frequency from %.3f MHz to %.3f MHz",
                activeAnnotation.getFreqLowerEdge() / 1e6,
                activeAnnotation.getFreqUpperEdge() / 1e6
            )
        );
        lowCutoff = activeAnnotation.getFreqLowerEdge();
        highCutoff = activeAnnotation.getFreqUpperEdge();

        // update the charts
        updateMagnitudeChart(data, sampleRate);
        updateFrequencyChart(data, sampleRate);
        updatePSDChart(data, sampleRate);

        ADC_LOGGER.info("Analysis Dialog updated for {} samples.", data[0].length);
    }

    /**
     * Clean up the potentially large data
     */
    public void performCleanup() {
        if (seriesMagn != null) {
            seriesMagn.clear();

        }
        if (seriesPSD != null) {
            seriesPSD.clear();

        }
        if (seriesFreq != null) {
            seriesFreq.clear();
        }

        ADC_LOGGER.info("Data released from AnalysisDialog");

    }

    @FXML
    public void handleClose(ActionEvent event) {
        performCleanup();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    /**
     * Handle PSD User interaction
     *
     * The PSD chart supports user selection of frequency bands and
     * the passband and noisefloor levels.
     * @param event Mouse event in the chart
     * @param x X-position of the event.
     * @param y Y-position of the event.
     */
    private void handlePsdInteraction(MouseEvent event, double x, double y) {
        XYPlot plot = viewPSD.getChart().getXYPlot();

        if (event.isShiftDown()) {
            // ====================================================================================
            // Shift (Set frequency bounds)
            // ====================================================================================
            if (firstFreqClick == null) {
                // First click: Just drop a temporary marker
                firstFreqClick = x;
                plot.clearDomainMarkers();
                addVerticalMarker(plot, x, "Point A", java.awt.Color.BLUE);

                // NOTE: disable button if only one frequency selected
                btnUpdateFreq.setDisable(true);
            } else {
                // Second click: Sort them and finalize
                this.lowCutoff = Math.min(firstFreqClick, x);
                this.highCutoff = Math.max(firstFreqClick, x);

                // Redraw clean markers at the sorted positions
                plot.clearDomainMarkers();
                addVerticalMarker(plot, lowCutoff, "Low Edge", java.awt.Color.BLUE);
                addVerticalMarker(plot, highCutoff, "High Edge", java.awt.Color.BLUE);

                // Update UI and enable the button
                txtAnnotFreq.setText(String.format("%.2f - %.2f Hz", lowCutoff, highCutoff));
                btnUpdateFreq.setDisable(false);

                // Reset for the next pair of clicks
                firstFreqClick = null;
            }
        } else if (event.isControlDown()) {
            // ====================================================================================
            // Ctrl (Specify noise floor)
            // ====================================================================================
            if (noiseFloorMarker != null) {
                plot.removeRangeMarker(noiseFloorMarker);
            }
            noiseFloorMarker = new ValueMarker(y);
            noiseFloorMarker.setPaint(java.awt.Color.RED);
            noiseFloorMarker.setLabel("Noise: " + String.format("%.1f dB", y));
            plot.addRangeMarker(noiseFloorMarker);
            this.currentNoiseFloor = y;
        } else {
            // ====================================================================================
            // Standard click: Set Passband Level (Horizontal)
            // ====================================================================================
            if (passbandMarker != null) {
                plot.removeRangeMarker(passbandMarker);
            }
            passbandMarker = new ValueMarker(y);
            passbandMarker.setPaint(java.awt.Color.GREEN);
            passbandMarker.setLabel("Signal: " + String.format("%.1f dB", y));
            plot.addRangeMarker(passbandMarker);

            this.currentPassbandLevel = y;
        }

        // Refresh the legend/subtitle
        updatePsdMetrics();
    }

    /**
     * Add Vertical Marker to display frequency range selection
     * @param plot
     * @param x The x-position for the vertical marker
     * @param label label of marker
     * @param color Color of marker
     */
    private void addVerticalMarker(XYPlot plot, double x, String label, java.awt.Color color) {
        ValueMarker marker = new ValueMarker(x);
        marker.setPaint(color);
        marker.setLabel(label);
        plot.addDomainMarker(marker);
    }

    /**
     * Update PSD metrics
     *
     * Calculate and display metrics selected by user
     */
    private void updatePsdMetrics() {
        // XYPlot plot = viewPSD.getChart().getXYPlot();

        // Retrieve values from markers (assuming you stored them as member variables)
        double snr = currentPassbandLevel - currentNoiseFloor;
        double centerFreq = (highCutoff + lowCutoff) / 2.0;
        double bandwidth = highCutoff - lowCutoff;

        String metrics = String.format(
            "Center: %.6f MHz | BW: %.6f MHz | SNR: %.1f dB",
            centerFreq / 1e6, bandwidth / 1e6, snr
        );

        // Update the chart subtitle
        TextTitle subtitle = new TextTitle(metrics);
        subtitle.setPaint(java.awt.Color.DARK_GRAY);
        subtitle.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));

        viewPSD.getChart().clearSubtitles();
        viewPSD.getChart().addSubtitle(subtitle);
    }

    /**
     * Update the button state for freq.  Disable if only on frequency
     * bound is selected.
     */
    private void checkFreqButtonState() {
        // Enable button only if both markers have been set by user clicks
        boolean ready = (lowCutoff != 0 && highCutoff != 0);
        btnUpdateFreq.setDisable(!ready);
    }

    /**
     * Support updating upper and low frequency based on user selection.
     * @param event
     */
    @FXML
    private void handleUpdateFreqFromMarkers(ActionEvent event) {
        if (activeAnnotation != null) {
            activeAnnotation.setFreqLowerEdge(lowCutoff);
            activeAnnotation.setFreqUpperEdge(highCutoff);
            txtAnnotFreq.setText(
                String.format("Updated Frequency from %.3f MHz to %.3f MHz",
                    lowCutoff / 1e6, highCutoff / 1e6));
            ADC_LOGGER.info("Annotation frequencies updated from PSD markers.");
        }
    }

    /**
     * Updates the annotation's label and comment fields
     *
     * @param event The Button being clicked
     */
    @FXML
    private void handleUpdate(ActionEvent event) {
        if (activeAnnotation == null) {
            return;
        }

        // update label
        activeAnnotation.setLabel(txtAnnotLabel.getText());

        // ------------------- if "Include Metrics" checkbox is selected  -------------------------
        String finalComment = txtAnnotComment.getText();
        if (chkIncludeMetrics.isSelected()) {
            // NOTE: Add metrics to the comments section of the annotation
            double snr = currentPassbandLevel - currentNoiseFloor;
            String metrics = String.format(
                "%nSNR: %.2f dB%nNoise: %.2f dB%nPower: %.2f dB",
                snr, currentNoiseFloor, currentPassbandLevel);
            finalComment += metrics;
        }

        // update comment
        activeAnnotation.setComment(finalComment);


        // Trigger your app's SigMF save logic here
        ADC_LOGGER.info("Annotation updated and metrics appended.");
    }
}
