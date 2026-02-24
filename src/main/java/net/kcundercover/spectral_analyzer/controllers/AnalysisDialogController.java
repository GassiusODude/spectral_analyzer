package net.kcundercover.spectral_analyzer.controllers;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;

import javafx.stage.Stage;
import java.awt.BasicStroke;
import org.jfree.chart.fx.interaction.ChartMouseEventFX;
import org.jfree.chart.fx.interaction.ChartMouseListenerFX;
import org.jfree.chart.plot.ValueMarker;
import javafx.scene.input.MouseEvent; // For the event trigger
import org.jfree.chart.title.TextTitle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.renderer.xy.SamplingXYLineRenderer;

import net.kcundercover.jdsp.signal.PowerSpectralDensity;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;

/**
 * Analysis Dialog Controller to go into further analysis of an annotation
 */
public class AnalysisDialogController {
    private static final Logger ADC_LOGGER = LoggerFactory.getLogger(AnalysisDialogController.class);

    /** Default Constructor */
    public AnalysisDialogController() {}

    /** Active annotation is a copy of the annotation,
     * introduced through setAnalysisData()
     */
    private SigMfAnnotation activeAnnotation;
    private double[][] data;
    private double centerFreq;
    private double sampleRate;      // NOTE: downconverted sample rate
    private double origSampleRate;  // NOTE: Original sample rate
    private double startTime;

    // variables for displaying and tracking user defined passband/noisefloor characteristics
    // ------------------------------------------------------------------------
    private ValueMarker passbandMarker;
    private ValueMarker noiseFloorMarker;
    private double currentPassbandLevel = Double.NaN;
    private double currentNoiseFloor = Double.NaN;

    // selection of frequency range in PSD
    // ------------------------------------------------------------------------
    private double userSelectLowFreq = 0;
    private double userSelectHighFreq = 0;
    private Double firstFreqClick = null; // Member variable
    private double userSelectStartTime = 0;
    private double userSelectStopTime = 0;
    private Double firstTimeClick = null; // Member variable


    // @FXML private ScatterChart<Number, Number> constellationChart;
    @FXML private StackPane containerMagn;
    @FXML private StackPane containerFreq;
    @FXML private StackPane containerPSD;

    // Exponential moving average (alpha slider)
    @FXML private Slider alphaMagnSlider;
    @FXML private Slider alphaFreqSlider;

    // section for displaying time/freq/label/comment from annotation
    // ------------------------------------------------------------------------
    @FXML private TextField txtAnnotLabel, txtAnnotTime, txtAnnotFreq;
    @FXML private TextArea txtAnnotComment;
    @FXML private Button btnUpdateFreq;
    @FXML private Button btnUpdateTime;

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

        viewMagn.addChartMouseListener(new ChartMouseListenerFX() {
            @Override
            public void chartMouseClicked(ChartMouseEventFX event) {
                MouseEvent mouseEvent = event.getTrigger();

                // Translate pixel coordinates to chart data values
                java.awt.geom.Rectangle2D dataArea = viewMagn.getCanvas().getRenderingInfo().getPlotInfo().getDataArea();
                XYPlot plot = (XYPlot) viewMagn.getChart().getPlot();

                double xValue = plot.getDomainAxis().java2DToValue(
                    mouseEvent.getX(), dataArea, plot.getDomainAxisEdge());
                double yValue = plot.getRangeAxis().java2DToValue(
                    mouseEvent.getY(), dataArea, plot.getRangeAxisEdge());

                handleMagnInteraction(event.getTrigger(), xValue, yValue);
            }

            @Override
            public void chartMouseMoved(ChartMouseEventFX event) {}
        });

        viewFreq.addChartMouseListener(new ChartMouseListenerFX() {
            @Override
            public void chartMouseClicked(ChartMouseEventFX event) {
                MouseEvent mouseEvent = event.getTrigger();

                // Translate pixel coordinates to chart data values
                java.awt.geom.Rectangle2D dataArea = viewFreq.getCanvas().getRenderingInfo().getPlotInfo().getDataArea();
                XYPlot plot = (XYPlot) viewFreq.getChart().getPlot();

                double xValue = plot.getDomainAxis().java2DToValue(
                    mouseEvent.getX(), dataArea, plot.getDomainAxisEdge());
                double yValue = plot.getRangeAxis().java2DToValue(
                    mouseEvent.getY(), dataArea, plot.getRangeAxisEdge());

                handleFreqInteraction(event.getTrigger(), xValue, yValue);
            }

            @Override
            public void chartMouseMoved(ChartMouseEventFX event) {}
        });

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


            // Listen for slider value changes
        alphaFreqSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateFrequencyChart();
        });
        alphaMagnSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateMagnitudeChart();
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
    private void updateMagnitudeChart() {
        double[][] data = this.data;
        double sampleRate = this.sampleRate;
        double alpha = alphaMagnSlider.getValue();

        seriesMagn.clear();

        double timeStep = 1.0 / sampleRate;
        double absValue = 0;
        double valueOld = 0;
        for (int i = 0; i < data[0].length; i++) {
            absValue = Math.hypot(data[0][i], data[1][i]);

            // Apply exponential moving average smoothing
            if (i == 0) {
                valueOld = absValue;
            } else {
                valueOld = alpha * absValue + (1 - alpha) * valueOld;
            }

            double logMag = 20 * Math.log10(valueOld);
            if (Double.isFinite(logMag)) {
                seriesMagn.add(this.startTime + i * timeStep, logMag, false);
            }
        }

        ValueAxis yAxis = viewMagn.getChart().getXYPlot().getRangeAxis();
        ((NumberAxis) yAxis).setAutoRangeIncludesZero(false);
        ((NumberAxis) yAxis).setAutoRange(true);

        seriesMagn.fireSeriesChanged();
        resetAxes(viewMagn, "Time (s)", "Magnitude (dB)");
    }

    /**
     * Update the frequency chart
     */
    private void updateFrequencyChart() {
        double[][] data = this.data;
        double sampleRate = this.sampleRate;

        seriesFreq.clear();
        double timeStep = 1.0 / sampleRate;
        double alpha = alphaFreqSlider.getValue();
        double vOld = 0.0;
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
            if (i == 1) {
                vOld = instFreq;
            } else {
                vOld = (alpha * instFreq) + (1 - alpha) * vOld;
            }
            seriesFreq.add(this.startTime + i * timeStep, vOld + this.centerFreq, false);
        }
        ValueAxis yAxis = viewFreq.getChart().getXYPlot().getRangeAxis();
        ((NumberAxis) yAxis).setAutoRangeIncludesZero(false);
        ((NumberAxis) yAxis).setAutoRange(true);

        seriesFreq.fireSeriesChanged();
        resetAxes(viewFreq, "Time (s)", "Frequency (Hz)");
    }

    /**
     * Update the Power Spectral Density chart
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     */
    private void updatePSDChart() {
        double[][] data = this.data;
        double sampleRate = this.sampleRate;

        seriesPSD.clear();

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

        ValueAxis yAxis = viewPSD.getChart().getXYPlot().getRangeAxis();
        ((NumberAxis) yAxis).setAutoRangeIncludesZero(false);
        ((NumberAxis) yAxis).setAutoRange(true);

        // FIXME: (not working)
        //        Attempt to compare against threshold used in spectrogram)
        // offset = 10 * Math.log10(sampleRate / freqAndPsd[0].length);
        offset = 0;

        for (int ind = 0; ind < freqAndPsd[0].length; ind++) {
            seriesPSD.add(
                freqAndPsd[0][ind] + this.centerFreq,
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
     * Format Frequency
     * @param freq Input frequency value
     * @return The formatted string
     */
    public String formatFreq(double freq) {
        if (freq >= 1e9) {
            return String.format("%.6f GHz", freq / 1e9);
        } else if (freq >= 1e6) {
            return String.format("%.6f MHz", freq / 1e6);
        } else if (freq >= 1e3) {
            return String.format("%.6f kHz", freq / 1e3);
        } else {
            return String.format("%.6f Hz", freq);
        }
    }


    /**
     * The entry point to this dialog from the main application
     *
     * @param data Downconverted IQ signal, double[2][N], data[0] is real, data[1] is imaginary
     * @param sampleRate Sample rate of the downconverted signal
     * @param origSampleRate the original sampling rate of the signal
     * @param startTime The start time
     * @param newAnnot The current SigMFAnnotation
     */
    public void setAnalysisData(double[][] data, double sampleRate, double origSampleRate, double startTime, SigMfAnnotation newAnnot) {
        if (data == null || data.length < 2) {
            return;
        }

        // =================================  Store info  =========================================
        // save a copy of the annotation (potentially updated to be returned)
        activeAnnotation = new SigMfAnnotation();
        activeAnnotation.copy(newAnnot);


        this.data = new double[data.length][];
        for (int i = 0; i < data.length; i++) {
            // Clone each row to ensure the values are truly independent
            this.data[i] = data[i].clone();
        }

        this.sampleRate = sampleRate;
        this.origSampleRate = origSampleRate;

        // update markers
        updateTimePlots(activeAnnotation.getSampleStart() / origSampleRate);
        updateTimePlots((activeAnnotation.getSampleStart() + activeAnnotation.getSampleCount()) / origSampleRate);
        updateFrequencyPlots(activeAnnotation.getFreqLowerEdge());
        updateFrequencyPlots(activeAnnotation.getFreqUpperEdge());

        this.centerFreq = 0.5 * (this.userSelectLowFreq + this.userSelectHighFreq);
        this.startTime = startTime;
        // =================================  Update GUI  =========================================
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

        // update the charts
        updateMagnitudeChart();
        updateFrequencyChart();
        updatePSDChart();

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

        this.data = null;

        ADC_LOGGER.info("Data released from AnalysisDialog");

    }

    /**
     * Handle the close event
     * @param event Handle closing of the dialog
     */
    @FXML
    public void handleClose(ActionEvent event) {
        performCleanup();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    /**
     * Convenience function to update time plots with time markers
     *
     * This track whether one time point is selected or both.  If both, this new
     * time resets to one new time selection.
     * @param newTime the new time selection
     */
    private void updateTimePlots(double newTime) {
        XYPlot plotMagn = viewMagn.getChart().getXYPlot();
        XYPlot plotFreq = viewFreq.getChart().getXYPlot();
        if (firstTimeClick == null) {
            // first time selection
            firstTimeClick = newTime;

            plotMagn.clearDomainMarkers();
            addVerticalMarker(plotMagn, newTime, "Point A", java.awt.Color.GREEN);
            plotFreq.clearDomainMarkers();
            addVerticalMarker(plotFreq, newTime, "Point A", java.awt.Color.GREEN);

            btnUpdateTime.setDisable(true);
        } else {
            this.userSelectStartTime = Math.min(firstTimeClick, newTime);
            this.userSelectStopTime = Math.max(firstTimeClick, newTime);

            // second time selected
            plotMagn.clearDomainMarkers();
            addVerticalMarker(plotMagn, userSelectStartTime, "Start Time", java.awt.Color.GREEN);
            addVerticalMarker(plotMagn, userSelectStopTime, "Stop Time", java.awt.Color.GREEN);
            plotFreq.clearDomainMarkers();
            addVerticalMarker(plotFreq, userSelectStartTime, "Start Time", java.awt.Color.GREEN);
            addVerticalMarker(plotFreq, userSelectStopTime, "Stop Time", java.awt.Color.GREEN);

            // reset firstTimeClick
            firstTimeClick = null;

            btnUpdateTime.setDisable(false);
        }

    }

    /**
     * This function draws frequency lines in both the PSD plot and the Instantaneous frequency plot
     * @param newFreq The new selected frequency
     */
    private void updateFrequencyPlots(double newFreq) {
        XYPlot plotPSD = viewPSD.getChart().getXYPlot();
        XYPlot plotFreq = viewFreq.getChart().getXYPlot();
        if (firstFreqClick == null) {
            // First click: Just drop a temporary marker
            firstFreqClick = newFreq;

            plotPSD.clearDomainMarkers();
            addVerticalMarker(plotPSD, newFreq, "Point A", java.awt.Color.BLUE);

            plotFreq.clearRangeMarkers();
            addHorizontalMarker(plotFreq, newFreq, "Point A", java.awt.Color.BLUE);

            // NOTE: disable button if only one frequency selected
            btnUpdateFreq.setDisable(true);

        } else {
            // Second click: Sort them and finalize
            this.userSelectLowFreq = Math.min(firstFreqClick, newFreq);
            this.userSelectHighFreq = Math.max(firstFreqClick, newFreq);

            // Redraw clean markers at the sorted positions
            plotPSD.clearDomainMarkers();
            addVerticalMarker(plotPSD, userSelectLowFreq, "Low Edge", java.awt.Color.BLUE);
            addVerticalMarker(plotPSD, userSelectHighFreq, "High Edge", java.awt.Color.BLUE);

            plotFreq.clearRangeMarkers();
            addHorizontalMarker(plotFreq, userSelectLowFreq, "Low Edge", java.awt.Color.BLUE);
            addHorizontalMarker(plotFreq, userSelectHighFreq, "High Edge", java.awt.Color.BLUE);

            // Update UI and enable the button
            txtAnnotFreq.setText(String.format("%.2f - %.2f Hz", userSelectLowFreq, userSelectHighFreq));
            btnUpdateFreq.setDisable(false);

            // Reset for the next pair of clicks
            firstFreqClick = null;
        }
    }

    /**
     * Handle the magnitude plot interaction
     * @param event Mouse click on the magnitude plot
     * @param x The horizontal position of the click
     * @param y The vertical position of the click
     */
    private void handleMagnInteraction(MouseEvent event, double x, double y) {
        updateTimePlots(x);
    }

    /**
     * Handle the interaction to the frequency plot
     * @param event The Mouse click
     * @param x The X-Position of the click
     * @param y The y-position of the click
     */
    private void handleFreqInteraction(MouseEvent event, double x, double y) {
        if (event.isShiftDown()) {
            // Set up frequency
            updateFrequencyPlots(y);
        } else {
            // set up time
            updateTimePlots(x);
        }
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
            updateFrequencyPlots(x);

        } else if (event.isControlDown()) {
            // ====================================================================================
            // Ctrl (Specify noise floor)
            // ====================================================================================
            if (noiseFloorMarker != null) {
                plot.removeRangeMarker(noiseFloorMarker);
            }
            noiseFloorMarker = new ValueMarker(y);
            noiseFloorMarker.setPaint(java.awt.Color.CYAN);
            noiseFloorMarker.setLabel("Noise: " + String.format("%.1f dB", y));
            noiseFloorMarker.setStroke(new BasicStroke(2));
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
            passbandMarker.setPaint(java.awt.Color.YELLOW);
            passbandMarker.setLabel("Signal: " + String.format("%.1f dB", y));
            passbandMarker.setStroke(new BasicStroke(2));
            plot.addRangeMarker(passbandMarker);


            this.currentPassbandLevel = y;
        }

        // Refresh the legend/subtitle
        updatePsdMetrics();
    }

    /**
     * Add Vertical Marker to display frequency range selection
     * @param plot The plot to add the marker to
     * @param x The x-position for the vertical marker
     * @param label label of marker
     * @param color Color of marker
     */
    private void addVerticalMarker(XYPlot plot, double x, String label, java.awt.Color color) {
        ValueMarker marker = new ValueMarker(x);
        marker.setPaint(color);
        marker.setLabel(label);
        marker.setStroke(new BasicStroke(2));
        plot.addDomainMarker(marker);
    }

    /**
     * Add horizontal marker to the plot
     * @param plot The plot to modify
     * @param x The value of the marker
     * @param label The label to apply to the marker
     * @param color The color of the marker
     */
    private void addHorizontalMarker(XYPlot plot, double x, String label, java.awt.Color color) {
        ValueMarker marker = new ValueMarker(x);
        marker.setPaint(color);
        marker.setLabel(label);
        marker.setStroke(new BasicStroke(2));
        plot.addRangeMarker(marker);
    }

    /**
     * Update PSD metrics
     *
     * Calculate and display metrics selected by user
     */
    private void updatePsdMetrics() {
        // Retrieve values from markers (assuming you stored them as member variables)
        double snr = currentPassbandLevel - currentNoiseFloor;
        double centerFreq = (userSelectHighFreq + userSelectLowFreq) / 2.0;
        double bandwidth = userSelectHighFreq - userSelectLowFreq;

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
     * Support updating time based on user selection.
     *
     * @param event Button event
     */
    @FXML
    private void handleUpdateTimeFromMarkers(ActionEvent event) {
        if (activeAnnotation != null) {

            // FIXME: calculate the start time in samples and duration in samples (with original sample rate)
            activeAnnotation.setSampleStart(Long.valueOf((long)(userSelectStartTime * this.origSampleRate)));
            long nSamples = Long.valueOf((long) ((userSelectStopTime - userSelectStartTime) * this.origSampleRate));
            activeAnnotation.setSampleCount(nSamples);

            txtAnnotTime.setText(
                String.format("Updated Time from %s to %s (Duration = %s)",
                    formatTime(userSelectStartTime),
                    formatTime(userSelectStopTime),
                    formatTime(userSelectStopTime - userSelectStartTime)
                ));
            ADC_LOGGER.info("Annotation Time updated from user selection.");
        }
    }


    /**
     * Support updating upper and low frequency based on user selection.
     * @param event Button event
     */
    @FXML
    private void handleUpdateFreqFromMarkers(ActionEvent event) {
        if (activeAnnotation != null) {
            activeAnnotation.setFreqLowerEdge(userSelectLowFreq);
            activeAnnotation.setFreqUpperEdge(userSelectHighFreq);
            txtAnnotFreq.setText(
                String.format("Updated Frequency from %s to %s (Bandwidth = %s)",
                    formatFreq(userSelectLowFreq),
                    formatFreq(userSelectHighFreq),
                    formatFreq(userSelectHighFreq - userSelectLowFreq)
                ));
            ADC_LOGGER.info("Annotation frequencies updated from PSD markers.");
        }
    }

    /**
     * Add measurements tot he comments section
     * @param e event of clicking the button
     */
    @FXML
    private void handleAddMeasurements(ActionEvent e) {
        // append to comment area
        String current = txtAnnotComment.getText();

        // --------------------------------------------------------------------
        // NOTE: Add metrics to the comments section
        // --------------------------------------------------------------------
        if (!Double.isNaN(currentPassbandLevel)) {
            current += String.format("%nSignal Power = %.2f dB/Hz", currentPassbandLevel);
        }
        if (!Double.isNaN(currentNoiseFloor)) {
            current += String.format("%nNoise Power = %.2f dB/Hz", currentNoiseFloor);
        }
        if (!Double.isNaN(currentPassbandLevel) && !Double.isNaN(currentNoiseFloor)) {
            double snr = currentPassbandLevel - currentNoiseFloor;
            current += String.format("%nSNR = %.2f dB", snr);
        }

        txtAnnotComment.setText(current);

    }
    /**
     * Updates the annotation's label and comment fields
     *
     * @param event The Button being clicked
     */
    @FXML
    private void handleUpdateLabelAndComment(ActionEvent event) {
        if (activeAnnotation == null) {
            return;
        }

        // update label
        activeAnnotation.setLabel(txtAnnotLabel.getText());

        // ------------------- if "Include Metrics" checkbox is selected  -------------------------
        String finalComment = txtAnnotComment.getText();

        // update comment
        activeAnnotation.setComment(finalComment);

        // Trigger your app's SigMF save logic here
        ADC_LOGGER.info("Annotation updated and metrics appended.");
    }
}
