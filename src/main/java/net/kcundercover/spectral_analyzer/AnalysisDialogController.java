package net.kcundercover.spectral_analyzer;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.apache.commons.math3.complex.Complex;
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

public class AnalysisDialogController {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisDialogController.class);

    @FXML private ScatterChart<Number, Number> constellationChart;
    @FXML private StackPane containerMagn;
    @FXML private StackPane containerFreq;
    @FXML private StackPane containerPSD;

    private ChartViewer viewMagn;
    private ChartViewer viewFreq;
    private ChartViewer viewPSD;
    private XYSeries seriesMagn;
    private XYSeries seriesFreq;
    private XYSeries seriesPSD;
    public void initialize() {
        // ====================  Magnitude Plot  ==============================

        seriesMagn = new XYSeries("Analysis Data");
        XYSeriesCollection dataset = new XYSeriesCollection(seriesMagn);

        // 2. Create Chart with High-Performance Renderer
        JFreeChart chartMagn = ChartFactory.createXYLineChart(
            "Magnitude of Segment", "Time (s)", "Log Magnitude", dataset);
        XYPlot plot = chartMagn.getXYPlot();
        plot.setRenderer(new SamplingXYLineRenderer());

        plot.getDomainAxis().setAutoRange(true);
        plot.getRangeAxis().setAutoRange(true);

        // 3. Initialize Viewer and add to UI
        viewMagn = new ChartViewer(chartMagn);
        containerMagn.getChildren().add(viewMagn);

        // ====================  Freq Plot  ==============================
        seriesFreq = new XYSeries("Analysis Data");
        XYSeriesCollection datasetFreq = new XYSeriesCollection(seriesFreq);

        JFreeChart chartFreq = ChartFactory.createXYLineChart(
            "Instantaneous Frequency", "Time (s)", "Frequency", datasetFreq);
        XYPlot plotFreq = chartMagn.getXYPlot();
        plotFreq.setRenderer(new SamplingXYLineRenderer());

        plotFreq.getDomainAxis().setAutoRange(true);
        plotFreq.getRangeAxis().setAutoRange(true);

        viewFreq = new ChartViewer(chartFreq);
        containerFreq.getChildren().add(viewFreq);

        // ====================  PSD Plot  ==============================
        seriesPSD = new XYSeries("Analysis Data");
        XYSeriesCollection datasetPSD = new XYSeriesCollection(seriesPSD);

        JFreeChart chartPSD = ChartFactory.createXYLineChart(
            "Instantaneous Frequency", "Time (s)", "Frequency", datasetPSD);
        XYPlot plotPSD = chartMagn.getXYPlot();
        plotPSD.setRenderer(new SamplingXYLineRenderer());

        plotPSD.getDomainAxis().setAutoRange(true);
        plotPSD.getRangeAxis().setAutoRange(true);

        viewPSD = new ChartViewer(chartPSD);
        containerPSD.getChildren().add(viewPSD);

    }

    private void resetAxes(ChartViewer viewer, String xLabel, String yLabel) {
        XYPlot plot = viewer.getChart().getXYPlot();
        plot.getDomainAxis().setLabel(xLabel);
        plot.getRangeAxis().setLabel(yLabel);
        plot.getDomainAxis().setAutoRange(true);
        plot.getRangeAxis().setAutoRange(true);
    }
    private void updateMagnitudeChart(double[][] data, double sampleRate) {
        seriesMagn.clear();
        double timeStep = 1.0 / sampleRate;
        for (int i = 0; i < data[0].length; i++) {
            double logMag = Math.log10(Math.hypot(data[0][i], data[1][i]));
            if (Double.isFinite(logMag)) {
                seriesMagn.add(i * timeStep, logMag, false);
            }
        }
        seriesMagn.fireSeriesChanged();
        resetAxes(viewMagn, "Time (s)", "Log Magnitude");
    }

    private void updateFrequencyChart(double[][] data, double sampleRate) {
        seriesFreq.clear();
        double timeStep = 1.0 / sampleRate;
        for (int i = 1; i < data[0].length; i++) {
            double phase1 = Math.atan2(data[1][i], data[0][i]);
            double phase2 = Math.atan2(data[1][i-1], data[0][i-1]);

            double dPhase = phase1 - phase2;
            // Wrap phase to [-PI, PI]
            if (dPhase > Math.PI) dPhase -= 2 * Math.PI;
            else if (dPhase < -Math.PI) dPhase += 2 * Math.PI;

            double instFreq = (dPhase / (2 * Math.PI)) * sampleRate;
            seriesFreq.add(i * timeStep, instFreq, false);
        }
        seriesFreq.fireSeriesChanged();
        resetAxes(viewFreq, "Time (s)", "Frequency (Hz)");
    }

    private void updatePSDChart(double[][] data, double sampleRate) {
        seriesPSD.clear();
        // Implementation requires an FFT library like JTransforms
        // 1. Combine data[0] and data[1] into complex array
        int psdNfft = 8192;
        double[][] freqAndPsd = PowerSpectralDensity.calculatePsdWelch(
            data, sampleRate, psdNfft);
        for (int ind=0; ind < psdNfft; ind++) {
            seriesPSD.add(freqAndPsd[0][ind], freqAndPsd[1][ind]);
        }

        seriesPSD.fireSeriesChanged();
        resetAxes(viewPSD, "Frequency (Hz)", "Power/Hz");
    }


    public void setAnalysisData(double[][] data, double sampleRate) {
        if (data == null || data.length < 2) return;

        // 1. Calculate and update Magnitude (Time Domain)
        updateMagnitudeChart(data, sampleRate);

        // 2. Calculate and update Instantaneous Frequency
        updateFrequencyChart(data, sampleRate);

        // 3. Calculate and update Power Spectral Density (PSD)
        updatePSDChart(data, sampleRate);

        logger.info("Analysis Dialog updated for {} samples.", data[0].length);
    }


    // public void setAnalysisData(double[][] data, double sampleRate) {
    //     if (data == null || data.length < 2) return;

    //     seriesMagn.clear();
    //     double timeStep = 1.0 / sampleRate;
    //     int pointCount = data[0].length;

    //     for (int i = 0; i < pointCount; i++) {
    //         double time = i * timeStep;

    //         // Calculate magnitude: abs value of vector (x, y)
    //         // Using Math.hypot is more accurate than Math.abs(x) + Math.abs(y)
    //         double magnitude = Math.hypot(data[0][i], data[1][i]);
    //         double logMag = Math.log10(magnitude);
    //         if (Double.isFinite(logMag) && !Double.isNaN(logMag)) {
    //            seriesMagn.add(time, logMag, false);
    //         }
    //     }
    //     seriesMagn.fireSeriesChanged();

    //     // 3. Update Labels and Focus
    //     XYPlot plot = viewMagn.getChart().getXYPlot();
    //     plot.getDomainAxis().setLabel("Time (s)");
    //     plot.getRangeAxis().setLabel("Log Magnitude");

    //     // Ensure the chart zooms to fit the data
    //     plot.getDomainAxis().setAutoRange(true);
    //     plot.getRangeAxis().setAutoRange(true);
    // }


    @FXML
    public void handleExportCsv(ActionEvent event) {

    }

    public void performCleanup() {
        if (seriesMagn != null) {
            seriesMagn.clear();
        }

        logger.info("Data released from JFreeChart seriesMagn");
    }

    @FXML
    public void handleClose(ActionEvent event) {
        performCleanup();

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
