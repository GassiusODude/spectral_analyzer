package net.kcundercover.spectral_analyzer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.PixelWriter;      // for fast drawing
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfMetadata;

@Component
@FxmlView("main-scene.fxml") // Links to the XML file above
public class MainController {

    private SigMfHelper sigMfHelper = new SigMfHelper();
    private long currentSampleOffset = 0; // Where we are in the file
    private int FFT_SIZE = 1024;
    private int visibleRows = 500;

    @Autowired
    private SpectralService spectralService;

    @FXML
    private ScrollBar fileScrollBar;

    @FXML
    public void initialize() {
        // Make canvas resize with the window
        spectrogramCanvas.widthProperty().bind(plotContainer.widthProperty());
        spectrogramCanvas.heightProperty().bind(plotContainer.heightProperty());

        // Redraw if resized
        spectrogramCanvas.widthProperty().addListener(e -> updateDisplay());
        spectrogramCanvas.heightProperty().addListener(e -> updateDisplay());
        // Listen for scrollbar changes
        fileScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.currentSampleOffset = newVal.longValue();
            updateDisplay(); // Redraw the canvas at the new offset
        });
    }

    @FXML
    public void handleOpen(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Signal File");

        // Set extension filters (useful for SDR/Audio files)
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SigMF (*.sigmf-meta)", "*.sigmf-meta"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Get the current window (owner) from the ActionEvent source
        // This centers the dialog over your application window
        Window ownerWindow = ((javafx.scene.control.MenuItem) event.getSource())
                                .getParentPopup().getOwnerWindow();

        File selectedFile = fileChooser.showOpenDialog(ownerWindow);

        if (selectedFile != null) {

            System.out.println("Selected file: " + selectedFile.getAbsolutePath());
            // Add logic here to pass the file to your JDSP processing service
            try {
                sigMfHelper.load(selectedFile.toPath());

                // print information about the meta file
                var meta = sigMfHelper.getMetadata();
                int bytesPerSample = meta.global().getBytesPerSample();
                long totalBytes = sigMfHelper.getDataBuffer().capacity();
                long totalSamples = totalBytes / bytesPerSample;

                fileScrollBar.setMin(0);
                fileScrollBar.setMax(totalSamples - (spectrogramCanvas.getHeight() * 1024));
                fileScrollBar.setValue(0);

                System.out.println("Total bytes = " + totalBytes);
                System.out.println("Total Samples = " + totalSamples);
                System.out.println("--- SigMF Loaded ---");
                System.out.println("Sample Rate: " + meta.global().sampleRate() + " Hz");
                System.out.println("Datatype: " + meta.global().datatype());
                System.out.println("Total Samples in File: " + totalSamples);

                Platform.runLater(() -> {
                    double canvasW = spectrogramCanvas.getWidth();

                    fileScrollBar.setMin(0);
                    // Ensure we don't calculate a negative Max
                    fileScrollBar.setMax(Math.max(0, totalSamples - (long)(canvasW * FFT_SIZE)));
                    fileScrollBar.setValue(0);
                    fileScrollBar.setBlockIncrement(canvasW);
                    updateDisplay();
                });

            } catch (Exception e) {
                e.printStackTrace();
                showErrorAlert("Failed to load SigMF file", e.getMessage());
            }
        }
    }

    @FXML
    public void handleExit(ActionEvent event) {
        Platform.exit();
    }

    @FXML
    public void handleAbout(ActionEvent event) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About Spectral Analyzer");
        alert.setHeaderText("Spectral Analyzer v0.0.1");
        alert.setContentText(
            "A JavaFX & Spring Boot application for signal processing.\n" +
            "Domain: net.kcundercover");

        // Optional: Set the owner window so it centers over the main app
        Window ownerWindow = ((javafx.scene.control.MenuItem) event.getSource())
                                .getParentPopup().getOwnerWindow();
        alert.initOwner(ownerWindow);

        // showAndWait() blocks user interaction with the main window until closed
        alert.showAndWait();
    }

    @FXML
    private StackPane plotContainer;

    @FXML
    private Canvas spectrogramCanvas;

    @FXML
    private Pane annotationOverlay;

    // Inside MainController.java or a helper class
    private Color getColorForMagnitude(double db) {
        // Normalize dB (assume range -100 to 0)
        double normalized = (db + 100) / 100.0;
        normalized = Math.clamp(normalized, 0.0, 1.0); // Java 21+ clamp

        // Simple "Heat" map: Black -> Blue -> Red -> Yellow
        if (normalized < 0.2) return Color.BLACK;
        if (normalized < 0.5) return Color.BLUE.interpolate(Color.RED, (normalized - 0.2) / 0.3);
        return Color.RED.interpolate(Color.YELLOW, (normalized - 0.5) / 0.5);
    }


    public void updateDisplay() {
        if (sigMfHelper.getDataBuffer() == null) return;

        // 1. Get exact pixel width
        int canvasW = (int) spectrogramCanvas.getWidth();
        int canvasH = (int) spectrogramCanvas.getHeight();
        if (canvasW <= 0) return; // Wait for layout

        int fftSize = 1024; // Freq resolution
        String dataType = sigMfHelper.getMetadata().global().datatype();
        int bytesPerSample = sigMfHelper.getMetadata().global().getBytesPerSample();

        // 2. waterfall[Time][Frequency]
        double[][] waterfall = new double[canvasW][fftSize];

        for (int t = 0; t < canvasW; t++) {
            // t * fftSize determines how many samples per horizontal pixel
            long sampleIndex = currentSampleOffset + ((long) t * fftSize);
            int byteOffset = (int) (sampleIndex * bytesPerSample);

            if (byteOffset + (fftSize * bytesPerSample) <= sigMfHelper.getDataBuffer().capacity()) {
                waterfall[t] = spectralService.computeMagnitudes(
                    sigMfHelper.getDataBuffer(),
                    byteOffset,
                    fftSize,
                    dataType
                );
            } else {
                // Fill with a very low dB value so the end of file is black
                waterfall[t] = new double[fftSize];
                java.util.Arrays.fill(waterfall[t], -150.0);
            }
        }
        renderSpectrogram(waterfall);
    }

    //  /**
    //  * This is your main rendering loop.
    //  * It connects the math (SpectralService) to the UI (Canvas).
    //  */
    private void renderSpectrogram(double[][] waterfallData) {
        GraphicsContext gc = spectrogramCanvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        int canvasW = (int) spectrogramCanvas.getWidth();
        int canvasH = (int) spectrogramCanvas.getHeight();
        int fftSize = waterfallData[0].length;

        for (int t = 0; t < canvasW; t++) {
            for (int f = 0; f < canvasH; f++) {
                // Map Canvas Y pixel to FFT Bin
                // We scale the fftSize down to the canvasH
                int fftBinIndex = (int) ((double) f / canvasH * fftSize);

                double db = waterfallData[t][fftBinIndex];
                Color color = getColorForMagnitude(db);

                // Draw time on X, Frequency on Y (inverted so low freq is bottom)
                pw.setColor(t, canvasH - 1 - f, color);
            }
        }
    }



    // Quick helper to show errors to the user
    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

}
