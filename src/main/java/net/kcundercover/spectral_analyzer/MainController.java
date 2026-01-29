package net.kcundercover.spectral_analyzer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.Parent;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfMetadata;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
@Component
@FxmlView("main-scene.fxml") // Links to the XML file above
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    private final AtomicBoolean redrawPending = new AtomicBoolean(false);
    private SigMfHelper sigMfHelper = new SigMfHelper();
    private long currentSampleOffset = 0; // Where we are in the file
    private int fft_size = 1024;
    private Path input_file;
    // Simple helper class for coordinate tracking
    private static class Delta { double x, y; }
    private final Map<String, Color> annotationStyles = new HashMap<>();

    @Autowired private SpectralService spectralService;

    // ------------------------- majority of GUI  -----------------------------
    // main plot of spectrogram, overlays
    @FXML private StackPane plotContainer;
    @FXML private Canvas spectrogramCanvas;
    @FXML private Pane annotationOverlay;

    // the horizontal and vertical axes
    @FXML private Pane frequencyRuler;
    @FXML private HBox timeAxis;
    @FXML private Region axisSpacer;
    @FXML private Region rightAxisSpacer;
    @FXML private ScrollBar fileScrollBar;

    // -----------------------------  controls  -------------------------------

    // FFT control and display
    @FXML private Slider nfftSlider;
    @FXML private Label lblNfftValue;

    // selection
    @FXML private Label lblSelectionStart;
    @FXML private Label lblSelectionDur;
    @FXML private Label lblFreqLow;
    @FXML private Label lblFreqHigh;
    @FXML private TextField selectionNameField;
    @FXML private TextArea selectionDescField;
    @FXML private CheckBox showAnnotationsCheckbox;
    @FXML private ColorPicker annotationColorPicker;



    /**
     * AnnotationGroup is used to track the UI {@code label} and {@code rect})
     * and the SigMFAnnotation {@code data}.
     *
     * {@code label} is used to show the type of annotation
     * {@code rect} is a overlaying Rectangle to show the time/frequency position
     * {@code data} is the Annotation information.
     */
    private static class AnnotationGroup {
        Rectangle rect;
        Label label;
        SigMfAnnotation data;
    }

    /** This mapping tracks the AnnotationGroup by the rectangle overlay */
    private final Map<Rectangle, AnnotationGroup> annotationMap = new HashMap<>();


    /**
     * Remove the {@code rect} and the associated label from
     * @param rect
     */
    public void removeAnnotation(Rectangle rect) {
        AnnotationGroup group = annotationMap.remove(rect);
        if (group != null) {
            annotationOverlay.getChildren().removeAll(
                group.rect,
                group.label
            );
        }
    }

    private Color defaultAnnotColor = Color.rgb(46, 204, 113, 1.0);

    // ============================================================================================
    //                                  Active Selection
    // ============================================================================================
    private double selectionX, selectionY;
    private Rectangle selectionRect = new Rectangle(0, 0, 0, 0);
    private long selectionStartSample = 0; // The file-based anchor
    private double selectionStartWidthSamples = 0; // How many samples wide it is
    private double selectionFreqLow = 0;
    private double selectionFreqHigh = 0;


    /**
     * Resets the variables regarding the user selection and
     * the UI components displaying that information
     */
    private void resetSelection() {
        // reset tracked values and UI display
        selectionStartSample = 0; // The file-based anchor
        selectionStartWidthSamples = 0; // How many samples wide it is
        selectionFreqLow = 0;
        selectionFreqHigh = 0;

        // NOTE: reset the UI display
        lblSelectionStart.setText("0 seconds");
        lblSelectionDur.setText("0 seconds");
        lblFreqLow.setText("No selection");
        lblFreqHigh.setText("No selection");

        selectionRect.setHeight(0);
        selectionRect.setWidth(0);
        selectionRect.setStroke(Color.rgb(255, 255, 255, 1.0));
        selectionRect.setStrokeWidth(2);
        selectionRect.setFill(Color.rgb(255, 255, 255, 0.3));
        selectionRect.setVisible(false);
    }

    // ============================================================================================
    //                                  Initialize
    // ============================================================================================

    @FXML
    public void initialize() {
        annotationColorPicker.setValue(Color.MAGENTA);
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

        nfftSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Enforce integer steps and calculate power of 2
            int exponent = newVal.intValue();
            fft_size = (int) Math.pow(2, exponent);

            // Update UI
            lblNfftValue.setText(String.valueOf(fft_size));

            // Trigger re-processing
            updateDisplay();
        });

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

        // ======================================
        //  Selection helper to test annotations
        // ======================================
        // Give the pane a "ghost" background so it's clickable
        annotationOverlay.setStyle("-fx-background-color: rgba(0,0,0,0);");

        // Ensure it fills the space but lets you click the canvas IF NO BOX IS THERE
        annotationOverlay.setPickOnBounds(false);
        annotationOverlay.prefWidthProperty().bind(spectrogramCanvas.widthProperty());
        annotationOverlay.prefHeightProperty().bind(spectrogramCanvas.heightProperty());

        // ======================================
        // Annotations Overlay Mouse handlers
        // ======================================
        annotationOverlay.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // NOTE: Use right click to say delete this current selection
                resetSelection();

            } else {
                // new selection starting...track X, Y position
                resetSelection();
                selectionX = e.getX();
                selectionY = e.getY();

                selectionRect.setX(selectionX);
                selectionRect.setY(selectionY);

                selectionRect.setVisible(true);
            }
        });

        annotationOverlay.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // secondary used to remove previous selection...no need to track
            } else if (selectionRect != null) {
                double w = Math.abs(e.getX() - selectionX);
                double h = Math.abs(e.getY() - selectionY);
                selectionRect.setWidth(w);
                selectionRect.setHeight(h);
                selectionRect.setX(Math.min(e.getX(), selectionX));
                selectionRect.setY(Math.min(e.getY(), selectionY));
            }
        });

        annotationOverlay.setOnMouseReleased(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // secondary used to remove previous selection...no need to track
            } else if (selectionRect != null) {
                int canvasW = (int) spectrogramCanvas.getWidth();
                this.selectionStartSample = currentSampleOffset + (long)((selectionRect.getX() / canvasW) * (canvasW * fft_size));

                this.selectionStartWidthSamples = (selectionRect.getWidth() / canvasW) * (canvasW * fft_size);
                int canvasH = (int) spectrogramCanvas.getHeight();
                double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
                double centerFreq = sigMfHelper.getMetadata().captures().get(0).frequency();
                selectionFreqHigh = centerFreq + sampleRate / 2 - (selectionRect.getY() / canvasH) * sampleRate;
                selectionFreqLow = selectionFreqHigh - (selectionRect.getHeight() / canvasH * sampleRate);

                selectionRect.setFill(Color.rgb(0, 120, 215, 0.3));
                selectionRect.setStroke(Color.rgb(0, 120, 215, 1.0));
                selectionRect.setStrokeWidth(2);

                logger.debug(
                    "\n\tSelection Start: {} | Duration: {} | Canvas Width: {} px\n",
                    String.format("%.3f ms", selectionStartSample / sampleRate * 1e3),
                    String.format("%.3f ms", selectionStartWidthSamples / sampleRate * 1e3),
                    String.format("%.1f", spectrogramCanvas.getWidth())
                );

                // update UI to display selection
                lblSelectionStart.setText(
                    String.format("%.3f ms", selectionStartSample / sampleRate * 1e3));
                lblSelectionDur.setText(
                    String.format("%.3f ms", selectionStartWidthSamples / sampleRate * 1e3));
                lblFreqLow.setText(String.format("%.6f MHz", selectionFreqLow / 1e6));
                lblFreqHigh.setText(String.format("%.6f MHz", selectionFreqHigh / 1e6));
            }
        });
    }

    // ============================================================================================
    //                                  Event Handlers
    // ============================================================================================

    /**
     * Open an input SigMF file
     * @param event The event that triggered this handler
     */
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

            logger.info("Selected file: {}", selectedFile.getAbsolutePath());

            // Add logic here to pass the file to your JDSP processing service
            try {
                sigMfHelper.load(selectedFile.toPath());

                // clear annotations and map
                annotationOverlay.getChildren().clear();
                annotationOverlay.getChildren().add(selectionRect);
                selectionRect.setVisible(false);
                annotationMap.clear();

                // track the input meta file
                input_file = selectedFile.toPath();

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

                logger.info(
                    "\n\t------- SigMF Loaded -------" +
                    "\n\tTotal bytes = {} bytes" +
                    "\n\tTotal Samples = {} samples" +
                    "\n\tSample Rate = {} Hz " +
                    "\n\tDatatype = {}",
                    totalBytes, totalSamples,
                    meta.global().sampleRate(),
                    meta.global().datatype());


                Platform.runLater(() -> {
                    double canvasW = spectrogramCanvas.getWidth();

                    fileScrollBar.setMin(0);
                    // Ensure we don't calculate a negative Max
                    fileScrollBar.setMax(Math.max(0, totalSamples - (long)(canvasW * fft_size)));
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

    /**
     * Save the current set of annotations to file.
     */
    @FXML
    private void handleSave() {

        List<SigMfAnnotation> sortedAnnotations = annotationMap.values().stream()
            .map(group -> group.data) // Access the field directly
            .sorted(Comparator.comparingLong(SigMfAnnotation::sampleStart))
            .toList();

        sigMfHelper.saveSigMF(sortedAnnotations);
        logger.info("SigMF saved: {} annotations written in chronological order.", sortedAnnotations.size());

    }

    @FXML
    private void handleAnalyzeSelection(ActionEvent event) {
        logger.warn("Handle Analyze Selection has not been implemented");
    }

    /**
     * Add an annotation based on the current user selection and UI input
     * This is triggered by the button to add annotations
     */
    @FXML
    private void handleAddAnnotation(ActionEvent event) {
        SigMfAnnotation selectAnnot = new SigMfAnnotation(
            (long) selectionStartSample,
            (long) selectionStartWidthSamples,
            selectionFreqLow,
            selectionFreqHigh,
            selectionNameField.getText(),
            selectionDescField.getText());

        // NOTE: create rect/label for this annotation.
        createRectangleForData(selectAnnot);

        // clear selection
        resetSelection();

        // refresh display
        updateAnnotationDisplay();
    }


    /**
     * Handle exiting
     * @param event
     */
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

    // ============================================================================================
    //                                  Helper functions
    // ============================================================================================


    /**
     * Convert a double value to color
     * @param db The double value
     * @return
     */
    private Color getColorForMagnitude(double db) {
        // Normalize dB (assume range -100 to 0)
        double normalized = (db + 100) / 100.0;
        normalized = Math.clamp(normalized, 0.0, 1.0); // Java 21+ clamp

        // Simple "Heat" map: Black -> Blue -> Red -> Yellow
        if (normalized < 0.2) return Color.BLACK;
        if (normalized < 0.5) return Color.BLUE.interpolate(Color.RED, (normalized - 0.2) / 0.3);
        return Color.RED.interpolate(Color.YELLOW, (normalized - 0.5) / 0.5);
    }

    /**
     * Core update method to redraw GUI
     */
    public void updateDisplay() {
        if (sigMfHelper.getDataBuffer() == null) return;

        // Get pixel width
        int canvasW = (int) spectrogramCanvas.getWidth();
        int canvasH = (int) spectrogramCanvas.getHeight();
        if (canvasW <= 0) return; // Wait for layout


        var global = sigMfHelper.getMetadata().global();
        String dataType = global.datatype();
        int bytesPerSample = global.getBytesPerSample();
        double sampleRate = global.sampleRate();

        // waterfall[Time][Frequency]
        double[][] waterfall = new double[canvasW][fft_size];

        for (int t = 0; t < canvasW; t++) {
            // t * fftSize determines how many samples per horizontal pixel
            long sampleIndex = currentSampleOffset + ((long) t * fft_size);
            int byteOffset = (int) (sampleIndex * bytesPerSample);

            if (byteOffset + (fft_size * bytesPerSample) <= sigMfHelper.getDataBuffer().capacity()) {
                waterfall[t] = spectralService.computeMagnitudes(
                    sigMfHelper.getDataBuffer(),
                    byteOffset,
                    fft_size,
                    dataType
                );
            } else {
                // Fill with a very low dB value so the end of file is black
                waterfall[t] = new double[fft_size];
                java.util.Arrays.fill(waterfall[t], -150.0);
            }
        }

        // update labels and Rectangles associated with annotations
        updateAnnotationDisplay();

        // selection rectangle
        if (selectionRect != null) {
            double canvasWD = spectrogramCanvas.getWidth();
            long samplesInView = (long)canvasW * fft_size;

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
            long sampleAtPixel = currentSampleOffset + ((long) xPixel * fft_size);
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
    /**
     * Prepares a {@code Label} and a {@code Rectangle} to represent the
     * provided annotation
     * @param data Current Annotation
     * @return
     */
    private Rectangle createRectangleForData(SigMfAnnotation cAnnot) {
        Rectangle rect = new Rectangle(); // Position will be set by updateDisplay()

        rect.setFill(annotationColorPicker.getValue());
        rect.setStroke(defaultAnnotColor);
        rect.setStrokeWidth(2);
        rect.setCursor(Cursor.HAND);

        // Create the Label
        // --------------------------------------
        Label label = new Label(cAnnot.label() != null ? cAnnot.label() : "Unnamed");
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-background-color: rgba(0,0,0,0.5);");
        label.setPadding(new Insets(2));
        label.setMouseTransparent(true); // So the label doesn't block clicking the box

        // Store the association
        // --------------------------------------
        AnnotationGroup group = new AnnotationGroup();
        group.rect = rect;
        group.label = label;
        group.data = cAnnot;
        annotationMap.put(rect, group);

        annotationOverlay.getChildren().addAll(rect, label);

        // set up the tool tip
        String tooltipText = String.format("Label: %s\nComment: %s",
                            cAnnot.label() != null ? cAnnot.label() : "N/A",
                            cAnnot.comment() != null ? cAnnot.comment() : "No comment");

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: #333; -fx-text-fill: #2ecc71;");
        tooltip.setShowDelay(javafx.util.Duration.millis(300)); // Show quickly
        Tooltip.install(rect, tooltip);

        // Add Right-Click Deletion
        rect.setOnMouseClicked(e -> {
            // do not let event bubble up to the pane
            e.consume();

            if (e.getButton() == MouseButton.SECONDARY) {
                // NOTE: Support removing selected annotation
                //       with the right mouse button.
                annotationOverlay.getChildren().remove(rect);
                removeAnnotation(rect);
            } else {
                // Note: update selection to point to the current annotation
                resetSelection();

                // prepare current annotation for analysis
                selectionStartSample = cAnnot.sampleStart();
                selectionStartSample = cAnnot.sampleCount();
                selectionFreqLow =  cAnnot.freqLowerEdge();
                selectionFreqHigh = cAnnot.freqUpperEdge();
                selectionRect.setVisible(true);

                //---------------  update UI  ------------------------
                selectionNameField.setText(cAnnot.label());
                selectionDescField.setText(cAnnot.comment());

                lblFreqLow.setText(
                    String.format("%.6f MHz", cAnnot.freqLowerEdge()/1e6));
                lblFreqHigh.setText(
                    String.format("%.6f MHz", cAnnot.freqUpperEdge()/1e6));
                lblSelectionStart.setText(String.format("%d samples", cAnnot.sampleStart()));
                lblSelectionDur.setText(String.format("%d samples", cAnnot.sampleCount()));


            }
        });
        return rect;
    }


    /**
     * Update annotations displayed
     *
     * This function will check if user enable/disabled annotations
     * It will pull from the default and custom color maps
     * It will determine which annotations are actually visible in
     * in the current time frame.
     */
    public void updateAnnotationDisplay() {

        annotationMap.forEach((rect, group) -> {
            double canvasW = spectrogramCanvas.getWidth();
            double canvasH = spectrogramCanvas.getHeight();
            double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
            double centerFreq = sigMfHelper.getMetadata().captures().get(0).frequency();

            // 1. Calculate Horizontal (Time) Position
            long offsetInSamples = group.data.sampleStart() - currentSampleOffset;
            double x = (double) offsetInSamples / fft_size;
            double width = (double) group.data.sampleCount() / fft_size;

            // 2. Calculate Vertical (Frequency) Position
            // Map Frequency back to 0.0-1.0 range of the current capture bandwidth
            double bw = sampleRate;
            double fLowRel = (group.data.freqLowerEdge() - (centerFreq - bw/2)) / bw;
            double fHighRel = (group.data.freqUpperEdge() - (centerFreq - bw/2)) / bw;

            // Invert for Canvas (0 is top)
            double y = (1.0 - fHighRel) * canvasH;
            double height = (fHighRel - fLowRel) * canvasH;

            // NOTE: check for custom color provided the label for this annotation
            if (annotationStyles.containsKey(group.label.getText())) {
                Color styleColor = annotationStyles.get(group.label.getText());
                rect.setStroke(styleColor);
                rect.setFill(styleColor.deriveColor(0,1,1,0.3)); // apply 0.3 alpha to the fill
            } else {
                rect.setStroke(annotationColorPicker.getValue());
                rect.setFill(annotationColorPicker.getValue().deriveColor(0,1,1,0.3)); // apply 0.3 alpha to the fill
            }

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

            if (!showAnnotationsCheckbox.isSelected()) {
                // keep hidden
                rect.setVisible(false);
                group.label.setVisible(false);
            }
        });
    }

    /**
     * Handle opening the dialog to map a label to a custom color scheme
     */
    @FXML
    private void handleOpenColorDialog(ActionEvent event) {
        showAnnotationStyleDialog();
    }

     /**
     * This is your main rendering loop.
     * It connects the math (SpectralService) to the UI (Canvas).
     *
     * This writes directly to the PixelWriter for speed.
     * @param waterfallData The input data
     */
    private void renderSpectrogram(double[][] waterfallData) {
        GraphicsContext gc = spectrogramCanvas.getGraphicsContext2D();
        PixelWriter pw = gc.getPixelWriter();

        int canvasW = (int) spectrogramCanvas.getWidth();
        int canvasH = (int) spectrogramCanvas.getHeight();
        int fft_size = waterfallData[0].length;

        for (int t = 0; t < canvasW; t++) {
            for (int f = 0; f < canvasH; f++) {
                // Map Canvas Y pixel to FFT Bin
                // We scale the fft_size down to the canvasH
                int fftBinIndex = (int) ((double) f / canvasH * fft_size);

                double db = waterfallData[t][fftBinIndex];
                Color color = getColorForMagnitude(db);

                // Draw time on X, Frequency on Y (inverted so low freq is bottom)
                pw.setColor(t, canvasH - 1 - f, color);
            }
        }
    }

    /**
     * Update the vertical frequency axis
     */
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



    // Quick helper to show errors to the user
    private void showErrorAlert(String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }



    /**
     * Show dialog to configure custom color to annotation label mappings.
     * This is used to apply different color schemes based on the
     * annotation id.
     */
    public void showAnnotationStyleDialog() {
        try {
            // Use your FxWeaver or FXMLLoader as before
            FXMLLoader loader = new FXMLLoader(getClass().getResource("annotation-style-dialog.fxml"));
            Parent content = loader.load();

            AnnotationStyleDialogController controller = loader.getController();

            // Pass the CURRENT map to the controller to populate its TableView
            controller.setStyles(annotationStyles);

            Dialog<Map<String, Color>> dialog = new Dialog<>();
            dialog.getDialogPane().setContent(content);
            // ... (title and button types)
            // This creates the physical buttons at the bottom of the dialog
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            // When the user clicks OK, grab the updated map from the controller
            dialog.setResultConverter(bt -> bt == ButtonType.OK ? controller.getUpdatedStyles() : null);

            dialog.showAndWait().ifPresent(updatedMap -> {
                this.annotationStyles.clear();
                this.annotationStyles.putAll(updatedMap);
                updateDisplay(); // Redraw canvas with new colors
            });

        } catch (IOException e) {
            logger.error("Failed to open styles dialog", e);
        }
    }



}
