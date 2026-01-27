package net.kcundercover.spectral_analyzer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tooltip;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.Cursor;
import javafx.scene.image.PixelWriter;      // for fast drawing
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;    // For the timeAxis container
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
//import org.w3c.dom.css.Rect;

import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfMetadata;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
@Component
@FxmlView("main-scene.fxml") // Links to the XML file above
public class MainController {
    private final AtomicBoolean redrawPending = new AtomicBoolean(false);
    private SigMfHelper sigMfHelper = new SigMfHelper();
    private long currentSampleOffset = 0; // Where we are in the file
    private int FFT_SIZE = 1024;
    private int visibleRows = 500;

    @Autowired
    private SpectralService spectralService;

    @FXML
    private ScrollBar fileScrollBar;

    // Inside MainController.java
    private static class AnnotationGroup {
        Rectangle rect;
        Label label;
        SigMfAnnotation data;
    }

    private final Map<Rectangle, AnnotationGroup> annotationMap = new HashMap<>();

    private Color defaultAnnotColor = Color.rgb(46, 204, 113, 1.0);

    // -----------------  selection rectange  -------------------------
    private double selectionX, selectionY;
    private Rectangle selectionRect = new Rectangle(0, 0, 0, 0);
    private Rectangle activeSelection;
    private long selectionStartSample = 0; // The file-based anchor
    private double selectionStartWidthSamples = 0; // How many samples wide it is


    @FXML
    public void initialize() {
        // initialize selection rectangle to be hidden
        annotationOverlay.getChildren().add(selectionRect);
        selectionRect.setVisible(false);

        spectrogramCanvas.widthProperty().bind(plotContainer.widthProperty());
        spectrogramCanvas.heightProperty().bind(plotContainer.heightProperty());

        ChangeListener<Number> resizeListener = (obs, o, n) -> {
            if (redrawPending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    redrawPending.set(false);
                    updateDisplay();
                });
            }
        };

        plotContainer.widthProperty().addListener(resizeListener);
        plotContainer.heightProperty().addListener(resizeListener);

        // // debug
        // plotContainer.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
        //     System.out.println("Plot bounds: " + newVal);
        // });

        frequencyRuler.prefHeightProperty().bind(spectrogramCanvas.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(plotContainer.widthProperty());
        clip.heightProperty().bind(plotContainer.heightProperty());
        plotContainer.setClip(clip);

        // Listen for scrollbar changes
        fileScrollBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.currentSampleOffset = newVal.longValue();
            updateDisplay(); // Redraw the canvas at the new offset
        });

        // ========================================================================================
        //  Selection helper to test annotations
        // ========================================================================================
        // Give the pane a "ghost" background so it's clickable
        annotationOverlay.setStyle("-fx-background-color: rgba(0,0,0,0);");
        // Ensure it fills the space but lets you click the canvas IF NO BOX IS THERE
        annotationOverlay.setPickOnBounds(false);
        annotationOverlay.prefWidthProperty().bind(spectrogramCanvas.widthProperty());
        annotationOverlay.prefHeightProperty().bind(spectrogramCanvas.heightProperty());

        annotationOverlay.setOnMousePressed(e -> {

            selectionX = e.getX();
            selectionY = e.getY();

            selectionRect.setX(selectionX);
            selectionRect.setY(selectionY);

            // reset settings
            selectionRect.setHeight(0);
            selectionRect.setWidth(0);
            selectionRect.setStroke(Color.rgb(255, 255, 255, 1.0));
            selectionRect.setStrokeWidth(2);
            selectionRect.setFill(Color.rgb(255, 255, 255, 0.2));

            int canvasW = (int) spectrogramCanvas.getWidth();
            this.selectionStartSample = currentSampleOffset + (long)((selectionX / canvasW) * (canvasW * FFT_SIZE));

            selectionRect.setVisible(true);
        });

        annotationOverlay.setOnMouseDragged(e -> {
            if (selectionRect != null) {
                double w = Math.abs(e.getX() - selectionX);
                double h = Math.abs(e.getY() - selectionY);
                selectionRect.setWidth(w);
                selectionRect.setHeight(h);
                selectionRect.setX(Math.min(e.getX(), selectionX));
                selectionRect.setY(Math.min(e.getY(), selectionY));
            }
        });

        annotationOverlay.setOnMouseReleased(e -> {

            if (selectionRect != null) {
                int canvasW = (int) spectrogramCanvas.getWidth();
                this.selectionStartWidthSamples = (selectionRect.getWidth() / canvasW) * (canvasW * FFT_SIZE);
                selectionRect.setFill(Color.rgb(0, 120, 215, 0.3));
                selectionRect.setStroke(Color.rgb(0, 120, 215, 1.0));
                selectionRect.setStrokeWidth(2);
                double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
                System.out.printf(
                    "Selection Start: %.3f ms | Duration: %.3f ms | Canvas Width: %.1f px\n",
                    selectionStartSample / sampleRate * 1e3,
                    selectionStartWidthSamples / sampleRate * 1e3,
                    spectrogramCanvas.getWidth() // Add this to debug the "overlap"
                );
            }
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

                // clear annotations and map
                annotationOverlay.getChildren().clear();
                selectionRect.setVisible(false);
                annotationOverlay.getChildren().add(selectionRect);
                annotationMap.clear();

                // load annotations
                List<SigMfAnnotation> fileAnnotations = sigMfHelper.getParsedAnnotations();
                for (SigMfAnnotation data : fileAnnotations) {
                    // Create the "Visual Proxy"
                    Rectangle rect = createRectangleForData(data);
                }

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
    private Pane frequencyRuler;

    @FXML
    private HBox timeAxis;

    @FXML
    private Region axisSpacer;

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
        var global = sigMfHelper.getMetadata().global();
        String dataType = global.datatype();
        int bytesPerSample = global.getBytesPerSample();
        double sampleRate = global.sampleRate();

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

        updateAnnotationDisplay();

        // selection rectangle
        if (selectionRect != null) {
            double canvasWD = spectrogramCanvas.getWidth();
            long samplesInView = (long)canvasW * FFT_SIZE;

            // Calculate new X based on current scroll offset
            double newX = ((double)(selectionStartSample - currentSampleOffset) / samplesInView) * canvasWD;
            double newWidth = (selectionStartWidthSamples / samplesInView) * canvasWD;

            selectionRect.setX(newX);
            selectionRect.setWidth(newWidth);

            // Hide the box if it scrolls completely off screen
            selectionRect.setVisible(newX + newWidth > 0 && newX < canvasW);
        }

        // spacer to avoid frequency axis
        timeAxis.getChildren().retainAll(axisSpacer);
        axisSpacer.setMinWidth(frequencyRuler.getWidth());
        axisSpacer.setPrefWidth(frequencyRuler.getWidth());

        // --- Update Time Axis ---
        // Add 5 time markers across the width
        for (int i = 0; i <= 4; i++) {
            int xPixel = (canvasW / 4) * i;
            long sampleAtPixel = currentSampleOffset + ((long) xPixel * fftSize);
            double seconds = (double) sampleAtPixel / sampleRate;

            Label timeLabel = new Label(String.format(" %.3fs", seconds));
            timeLabel.setTextFill(Color.LIGHTGRAY);
            timeLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");

            // Use a Region/Spacer to push labels apart if not using absolute positioning
            timeAxis.getChildren().add(timeLabel);
            if (i < 4) {
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                timeAxis.getChildren().add(spacer);
            }
        }

        updateRulers();
        renderSpectrogram(waterfall);
    }

    private Rectangle createRectangleForData(SigMfAnnotation data) {
        Rectangle rect = new Rectangle(); // Position will be set by updateDisplay()
        rect.setFill(Color.rgb(0, 120, 215, 0.4));
        rect.setStroke(defaultAnnotColor);
        rect.setStrokeWidth(2);
        rect.setCursor(Cursor.HAND);

        // 1. Create the Label
        Label label = new Label(data.label() != null ? data.label() : "Unnamed");
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-background-color: rgba(0,0,0,0.5);");
        label.setPadding(new Insets(2));
        label.setMouseTransparent(true); // So the label doesn't block clicking the box

        // 2. Store the association
        AnnotationGroup group = new AnnotationGroup();
        group.rect = rect;
        group.label = label;
        group.data = data;
        annotationMap.put(rect, group);

        annotationOverlay.getChildren().addAll(rect, label);


        // set up the tool tip
        String tooltipText = String.format("Label: %s\nComment: %s",
                            data.label() != null ? data.label() : "N/A",
                            data.comment() != null ? data.comment() : "No comment");

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: #333; -fx-text-fill: #2ecc71;");
        tooltip.setShowDelay(javafx.util.Duration.millis(300)); // Show quickly
        Tooltip.install(rect, tooltip);

        // Add Right-Click Deletion
        rect.setOnMouseClicked(e -> {
            // do not let event bubble up to the pane
            e.consume();

            if (e.getButton() == MouseButton.SECONDARY) {
                annotationOverlay.getChildren().remove(rect);
                annotationMap.remove(rect);
            }
        });


        return rect;
    }

    public void updateAnnotationDisplay() {

        annotationMap.forEach((rect, group) -> {
            double canvasW = spectrogramCanvas.getWidth();
            double canvasH = spectrogramCanvas.getHeight();
            double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
            double centerFreq = sigMfHelper.getMetadata().captures().get(0).frequency();

            // 1. Calculate Horizontal (Time) Position
            long offsetInSamples = group.data.sampleStart() - currentSampleOffset;
            double x = (double) offsetInSamples / FFT_SIZE;
            double width = (double) group.data.sampleCount() / FFT_SIZE;

            // 2. Calculate Vertical (Frequency) Position
            // Map Frequency back to 0.0-1.0 range of the current capture bandwidth
            double bw = sampleRate;
            double fLowRel = (group.data.freqLowerEdge() - (centerFreq - bw/2)) / bw;
            double fHighRel = (group.data.freqUpperEdge() - (centerFreq - bw/2)) / bw;

            // Invert for Canvas (0 is top)
            double y = (1.0 - fHighRel) * canvasH;
            double height = (fHighRel - fLowRel) * canvasH;

            rect.setX(x);
            rect.setWidth(width);
            rect.setY(y);
            rect.setHeight(height);

            rect.setVisible(x + width > 0 && x < canvasW);

                    // Position the label at the top-left of the rectangle
            group.label.setLayoutX(rect.getX());
            group.label.setLayoutY(rect.getY() - 20); // Position slightly above the box

            // Match visibility
            group.label.setVisible(rect.isVisible());
        });
    }

     /**
     * This is your main rendering loop.
     * It connects the math (SpectralService) to the UI (Canvas).
     */
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

    private void updateRulers() {
        frequencyRuler.getChildren().clear();
        if (sigMfHelper.getMetadata() == null) return;

        var global = sigMfHelper.getMetadata().global();
        var capture = sigMfHelper.getMetadata().captures().get(0);

        double centerFreq = capture.frequency();
        double sampleRate = global.sampleRate();
        double canvasH = spectrogramCanvas.getHeight();

        // Create 5 markers: Top, Mid-Top, Center, Mid-Bottom, Bottom
        int numMarkers = 5;
        for (int i = 0; i < numMarkers; i++) {
            // Calculate frequency for this marker
            // i=0 is top (+fs/2), i=4 is bottom (-fs/2)
            double percentage = (double) i / (numMarkers - 1);
            double freqOffset = (sampleRate / 2.0) - (percentage * sampleRate);
            double freqMhz = (centerFreq + freqOffset) / 1e6;

            Label label = new Label(String.format("%.2f MHz", freqMhz));
            label.setTextFill(Color.LIGHTGRAY);
            label.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");

            // Position the label
            // We subtract a small amount (5px) to center the text vertically on the tick
            double yPos = (percentage * canvasH) - 5;
            label.setLayoutY(Math.max(0, yPos));
            label.setLayoutX(5); // Slight margin from the left edge

            frequencyRuler.getChildren().add(label);
        }
    }

    public void printSelectionDetails(Rectangle rect) {
        var global = sigMfHelper.getMetadata().global();
        var capture = sigMfHelper.getMetadata().captures().get(0);

        double canvasW = spectrogramCanvas.getWidth();
        double canvasH = spectrogramCanvas.getHeight();
        double sampleRate = global.sampleRate();
        double centerFreq = capture.frequency();

        // --- Frequency Mapping (Vertical) ---
        // Remember: we inverted Y so low freq is at the bottom
        double yBottom = canvasH - (rect.getY() + rect.getHeight());
        double yTop = canvasH - rect.getY();

        double freqStart = centerFreq + ((yBottom / canvasH) - 0.5) * sampleRate;
        double freqEnd = centerFreq + ((yTop / canvasH) - 0.5) * sampleRate;
        double bandwidth = freqEnd - freqStart;

        // --- Time Mapping (Horizontal) ---
        long sampleStart = currentSampleOffset + (long)((rect.getX() / canvasW) * (canvasW * FFT_SIZE));
        double timeStart = (double) sampleStart / sampleRate;
        double duration = (rect.getWidth() / canvasW) * (canvasW * FFT_SIZE) / sampleRate;

        System.out.printf(
            "Selection: %.2f MHz to %.2f MHz | BW: %.2f kHz | Duration: %.3f ms | Time Start: %.3f ms%n",
            freqStart/1e6, freqEnd/1e6, bandwidth/1e3, duration*1000,
            this.selectionStartSample / sigMfHelper.getMetadata().global().sampleRate() * 1e3);
    }

    // Simple helper class for coordinate tracking
    private static class Delta { double x, y; }


    // Quick helper to show errors to the user
    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

}
