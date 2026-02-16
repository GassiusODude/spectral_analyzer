package net.kcundercover.spectral_analyzer.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;

import javafx.scene.control.Dialog;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.Cursor;
import javafx.scene.image.PixelWriter;      // for fast drawing
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import javafx.scene.layout.HBox;    // For the timeAxis container
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.paint.Color;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.shape.Line;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import net.kcundercover.spectral_analyzer.data.IqData;
import net.kcundercover.spectral_analyzer.rest.Capability;
import net.kcundercover.spectral_analyzer.rest.RestHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
import net.kcundercover.spectral_analyzer.services.AsyncExtractDownConvertService;
import net.kcundercover.spectral_analyzer.services.ExtractDownConvertService;
import net.kcundercover.spectral_analyzer.services.SpectralService;

/**
 * Main controller for FXML
 */
@Component
@FxmlView("main-scene.fxml")
public class MainController {
    /** Default constructor */
    public MainController(){

    }
    private static final Logger MC_LOGGER = LoggerFactory.getLogger(MainController.class);
    private RestHelper restHelper = new RestHelper();
    // throttle updates
    private final AtomicBoolean redrawPending = new AtomicBoolean(false);

    private SigMfHelper sigMfHelper = new SigMfHelper();
    private long currentSampleOffset = 0; // Where we are in the file
    private int fftSize;
    private double minDecibel = -100.0;
    private double maxDecibel = 0.0;

    // track the input file path
    private File lastOpenedDirectory;
    private Path inputFile;
    private long totalSamples;


    /**
     * Custom style for annotation based on label
     */
    private final Map<String, Color> annotationStyles = new HashMap<>();

    @Autowired private SpectralService spectralService;
    @Autowired private ExtractDownConvertService downConvertService;
    @Autowired private AsyncExtractDownConvertService asyncDownConvertService;

    // ------------------------- majority of GUI  -----------------------------
    // main plot of spectrogram, overlays
    @FXML private StackPane plotContainer;
    @FXML private Canvas spectrogramCanvas;
    @FXML private Pane annotationOverlay;
    @FXML private VBox rightPanel;

    // the horizontal and vertical axes
    @FXML private Pane frequencyRuler;
    @FXML private HBox timeAxis;
    @FXML private Pane timeLabelsContainer;
    @FXML private Region leftAxisSpacer;
    @FXML private Region rightAxisSpacer;
    @FXML private ScrollBar fileScrollBar;

    // -----------------------------  controls  -------------------------------
    // Menu
    @FXML CheckMenuItem fastDownConverter;
    @FXML ColorPicker selectColorPicker;
    @FXML CheckMenuItem menuItemShowAnnotations;

    // ==========================================
    // Right Panel
    // ==========================================

    // FFT control and display
    @FXML private Slider nfftSlider;
    @FXML private Label lblNfftValue;

    // Annotations
    @FXML private CheckBox showAnnotationsCheckbox;
    @FXML private ColorPicker annotationColorPicker;

    // selection
    @FXML private Label lblSelectionStart;
    @FXML private Label lblSelectionDur;
    @FXML private Label lblFreqLow;
    @FXML private Label lblFreqHigh;
    @FXML private TextField selectionNameField;
    @FXML private TextArea selectionDescField;
    @FXML private Button btnAnalyzeSelection;


    /**
     * AnnotationGroup is used to track the UI {@code label} and {@code rect})
     * {@code tooltip} and the SigMFAnnotation {@code data}.
     *
     * {@code label} is used to show the type of annotation
     * {@code rect} is a overlaying Rectangle to visually show time/frequency position
     * {@code data} is the SigMF Annotation information.
     * {@code tooltip} is the comment from the annotation shown as a tooltip for the rectangle.
     */
    private static class AnnotationGroup {
        Rectangle rect;
        Label label;
        SigMfAnnotation data;
        Tooltip tooltip;
    }

    /** This mapping tracks the AnnotationGroup based on the rectangle overlay */
    private final Map<Rectangle, AnnotationGroup> annotationMap = new HashMap<>();

    /**
     * Remove the {@code rect} and the associated label/tooltip
     * @param rect The rectangle object used as key to the {@code annotationMap}
     */
    public void removeAnnotation(Rectangle rect) {
        // remove the entry from annotationMap
        AnnotationGroup group = annotationMap.remove(rect);

        if (group != null) {
            // Remote tooltips
            if (group.tooltip != null) {
                Tooltip.uninstall(group.rect, group.tooltip);
            }

            // remove visuals linked to the annotationOverlay panel
            annotationOverlay.getChildren().removeAll(group.rect, group.label);
            MC_LOGGER.info("Annotation and Tooltip removed for: {}", group.data.getLabel());
        }
    }


    // ============================================================================================
    //                                  Active Selection
    // ============================================================================================
    private double selectionX, selectionY;
    private Rectangle selectionRect = new Rectangle(0, 0, 0, 0);
    private long selectionStartSample = 0; // The file-based anchor
    private double selectionStartWidthSamples = 0; // How many samples wide it is
    private double selectionFreqLow = 0;
    private double selectionFreqHigh = 0;
    private boolean selectionComplete = false;
    private SigMfAnnotation selectionAnnotation;


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
        selectionComplete = false;

        // NOTE: reset the UI display
        selectionNameField.setText("Unknown");
        selectionDescField.setText("");
        lblSelectionStart.setText("0 seconds");
        lblSelectionDur.setText("0 seconds");
        lblFreqLow.setText("No selection");
        lblFreqHigh.setText("No selection");

        selectionRect.setHeight(0);
        selectionRect.setWidth(0);
        selectionRect.setStroke(selectColorPicker.getValue());
        selectionRect.setStrokeWidth(8);
        selectionRect.setFill(selectColorPicker.getValue()
            .deriveColor(0, 1, 1, 0.3));

        selectionRect.setVisible(false);
    }

    // ============================================================================================
    //                                  Initialize
    // ============================================================================================

    /**
     * Initialize method for Controller
     */
    @FXML
    public void initialize() {
        annotationColorPicker.setValue(Color.MAGENTA);
        selectColorPicker.setValue(Color.LIME);
        resetSelection();

        comboColorMap.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updateDisplay();
            }
        });

        minDecibel = Double.parseDouble(minDbInput.getText());
        maxDecibel = Double.parseDouble(maxDbInput.getText());
        // initialize selection rectangle to be hidden
        annotationOverlay.getChildren().add(selectionRect);
        selectionRect.setVisible(false);

        // track the width of the right panel and update right axis spacer
        rightPanel.widthProperty().addListener((obs, oldVal, newVal) -> {
            double width = newVal.doubleValue();
            rightAxisSpacer.setMinWidth(width);
            rightAxisSpacer.setPrefWidth(width);
            rightAxisSpacer.setMaxWidth(width);
        });

        spectrogramCanvas.widthProperty().bind(plotContainer.widthProperty());
        spectrogramCanvas.heightProperty().bind(plotContainer.heightProperty());

        menuItemShowAnnotations.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateAnnotationDisplay();
        });
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

        // initialize fft by nfftSlider
        fftSize = (int) Math.pow(2, (int) nfftSlider.getValue());
        nfftSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            // Enforce integer steps and calculate power of 2
            int exponent = newVal.intValue();
            fftSize = (int) Math.pow(2, exponent);

            // Update UI
            lblNfftValue.setText(String.valueOf(fftSize));

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
            // reset selection (used with analyze selection)
            selectionAnnotation = null;
            btnAnalyzeSelection.setDisable(true);

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
                int canvasH = (int) spectrogramCanvas.getHeight();
                double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
                double centerFreq = sigMfHelper.getMetadata().captures().get(0).frequency();

                // track the selection information
                // --------------------------------------------------
                this.selectionStartSample = currentSampleOffset + (long)((selectionRect.getX() / canvasW) * (canvasW * fftSize));
                this.selectionStartWidthSamples = (selectionRect.getWidth() / canvasW) * (canvasW * fftSize);
                selectionFreqHigh = centerFreq + sampleRate / 2 - (selectionRect.getY() / canvasH) * sampleRate;
                selectionFreqLow = selectionFreqHigh - (selectionRect.getHeight() / canvasH * sampleRate);
                selectionComplete = true;

                // set the characteristics of the selection
                // --------------------------------------------------
                selectionRect.setStroke(selectColorPicker.getValue());
                selectionRect.setFill(selectColorPicker.getValue().deriveColor(0, 1, 1, 0.3));
                selectionRect.setStrokeWidth(2);

                // update UI to display selection
                // --------------------------------------------------
                double startTime = (double) selectionStartSample / sampleRate;
                String startUnit = (startTime >= 1.0) ? "s" : "ms";
                double startValue = (startTime >= 1.0) ? startTime : startTime * 1e3;

                double durTime = (double) selectionStartWidthSamples / sampleRate;
                String durUnit = (durTime >= 1.0) ? "s" : "ms";
                double durValue = (durTime >= 1.0) ? durTime : durTime * 1e3;


                lblSelectionStart.setText(
                    String.format("%.3f %s", startValue, startUnit));
                lblSelectionDur.setText(
                    String.format("%.3f %s", durValue, durUnit));
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
            // Set to last directory if it exists
        if (lastOpenedDirectory != null && lastOpenedDirectory.exists()) {
            fileChooser.setInitialDirectory(lastOpenedDirectory);
        }
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
            // track the directory for next time this is called.
            lastOpenedDirectory = selectedFile.getParentFile();
            MC_LOGGER.info("Selected file: {}", selectedFile.getAbsolutePath());

            // Add logic here to pass the file to your JDSP processing service
            try {
                sigMfHelper.load(selectedFile.toPath());

                // clear annotations and map
                annotationOverlay.getChildren().clear();
                annotationOverlay.getChildren().add(selectionRect);
                selectionRect.setVisible(false);
                annotationMap.clear();

                // track the input meta file
                inputFile = selectedFile.toPath();
                ((Stage) ownerWindow).setTitle(
                    String.format("Spectral Analyzer(file='%s')", inputFile.getFileName()));
                // load annotations
                List<SigMfAnnotation> fileAnnotations = sigMfHelper.getParsedAnnotations();
                for (SigMfAnnotation data : fileAnnotations) {
                    // Create the "Visual Proxy"
                    createRectangleForData(data);
                }

                // print information about the meta file
                var meta = sigMfHelper.getMetadata();
                int bytesPerSample = meta.global().getBytesPerSample();
                long totalBytes = sigMfHelper.getDataBuffer().capacity();
                totalSamples = totalBytes / bytesPerSample;

                fileScrollBar.setMin(0);
                fileScrollBar.setMax(totalSamples - (spectrogramCanvas.getHeight() * fftSize));
                fileScrollBar.setValue(0);

                MC_LOGGER.info(
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
                    fileScrollBar.setMax(Math.max(0, totalSamples - (long)(canvasW * fftSize)));
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
            .sorted(Comparator.<SigMfAnnotation>comparingLong(SigMfAnnotation::sampleStart))
            .toList();

        sigMfHelper.saveSigMF(sortedAnnotations);
        MC_LOGGER.info("SigMF saved: {} annotations written in chronological order.", sortedAnnotations.size());
    }

    @FXML TextField minDbInput;
    @FXML TextField maxDbInput;

    /**
     * Handle change in the decibel to color mapping.
     * @param event
     */
    @FXML
    private void handleScaleUpdate(ActionEvent event) {
        double minDb = Double.parseDouble(minDbInput.getText());
        double maxDb = Double.parseDouble(maxDbInput.getText());

        if (minDb >= maxDb) {
            // reject bounds
            minDbInput.setText(String.valueOf(minDecibel));
            maxDbInput.setText(String.valueOf(maxDecibel));
        } else {
            // update bounds
            minDecibel = minDb;
            maxDecibel = maxDb;
            updateDisplay();
        }
    }

    /**
     * Handle Analyze Selection Button event
     *
     * Open dialog for user to analyze the selection
     * @param event Button press that triggered this handler
     */
    @FXML
    private void handleAnalyzeSelection(ActionEvent event) {
        if (!selectionComplete) {
            showErrorAlert(
                "Selection Incomplete",
                "Select time/freq segment or click on annotation");
            return;
        }

        double inputFs = sigMfHelper.getMetadata().global().sampleRate();
        double inputFc = sigMfHelper.getMetadata().captures().get(0).frequency();

        // =======================================================
        // NOTE: Send a little exta bw and time for PSD analysis
        // =======================================================
        // NOTE: extend the bandwidth by 20 % (allow for improved analysis)
        double currBw = (selectionFreqHigh - selectionFreqLow) * 1.2;
        double center = (selectionFreqHigh + selectionFreqLow) / 2.0 - inputFc;

        // NOTE: extend time by
        long targetStart;
        if (selectionStartSample - (long) (selectionStartWidthSamples * 0.1) < 0) {
            targetStart = 0L;
        } else {
            targetStart = selectionStartSample - (long) (selectionStartWidthSamples * 0.1);
        }

        long targetWidth;
        if (selectionStartSample + selectionStartWidthSamples * 1.1 > totalSamples) {
            // end of file, get as
            targetWidth = totalSamples - targetStart;
        } else {
            targetWidth = (long) (selectionStartWidthSamples * 1.1) + (selectionStartSample - targetStart);
        }

        int down = (int) Math.ceil(inputFs / currBw);

        double targetFs = inputFs / down;

        final double finalTargetFs = targetFs;
        final double finalStartTime = targetStart / inputFs;
        String dataType = sigMfHelper.getMetadata().global().datatype();
        Window owner = ((Node) event.getSource())
                .getScene().getWindow();
        // Run off-thread to avoid [lication Thread] freezes
        // ==========================================================
        // NOTE: Runs downconverter to supply analysis dialog
        CompletableFuture.supplyAsync(() -> downConvertService.extractAndDownConvert(
                sigMfHelper.getDataBuffer(),
                targetStart,
                (int) targetWidth,
                dataType,
                center / inputFs,
                down,
                fastDownConverter.isSelected()
        )).thenAccept(data -> {
            // Open the new Dialog on the UI thread
            Platform.runLater(() -> openAnalysisDialog(owner, data, finalTargetFs, finalStartTime));
        });
    }

    /**
     * Open the Analysis Dialog
     *
     * Pass the downconverted signal to the diagnosis dialog for processing.
     * Also links the annotation to dialog for potential updates
     * @param data Downconverted signal
     * @param fs Sample rate of the down converted signal
     */
    private void openAnalysisDialog(Window owner, double[][] data, double fs, double startTime) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("analysis-dialog.fxml"));
            Parent root = loader.load();
            double origSampleRate = sigMfHelper.getMetadata().global().sampleRate();

            // Get the controller and "inject" the data
            // ------------------------------------------------------
            AnalysisDialogController controller = loader.getController();
            controller.setAnalysisData(data, fs, origSampleRate, startTime, selectionAnnotation);

            // Display
            // ------------------------------------------------------
            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.setScene(new Scene(root));
            stage.setOnCloseRequest(event -> {
                controller.performCleanup(); // Move your logger and data release here
            });

            stage.initModality(Modality.APPLICATION_MODAL); // Allows user to interact with both windows
            stage.showAndWait();

            // Update annotation display
            // ------------------------------------------------------
            selectionAnnotation.copy(controller.getUpdatedAnnotation());
            updateRect(selectionRect, selectionAnnotation); // update rect if annotation has updated.

            // NOTE: potential updates to labels/comments and frequency bounds)
            updateAnnotationDisplay();

        } catch (IOException e) {
            MC_LOGGER.error("Failed to open Analysis Dialog", e);
        }
    }


    /**
     * Add an annotation based on the current user selection and UI input
     * This is triggered by the button to add annotations
     */
    @FXML
    private void handleAddAnnotation(ActionEvent event) {
        // prepare the new annotation from the selection information
        SigMfAnnotation selectAnnot = new SigMfAnnotation(
            (long) selectionStartSample,
            (long) selectionStartWidthSamples,
            selectionFreqLow,
            selectionFreqHigh,
            selectionNameField.getText(),
            selectionDescField.getText());

        // reset the selection variables
        resetSelection();

        MouseEvent fakeClick = new MouseEvent(
            MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
            MouseButton.PRIMARY, 1,
            false, false, false, false, // Shift, Ctrl, Alt, Meta
            true, false, false, true,   // Primary, Middle, Secondary, Synthesized
            false, false, null
        );

        // NOTE: create rect/label for this annotation.
        //       trigger mouse click event for rect to "select" it
        //       so that it can jump straight into analysis
        Rectangle rect = createRectangleForData(selectAnnot);
        rect.fireEvent(fakeClick);

        // refresh display
        updateAnnotationDisplay();
    }


    /**
     * Handle exiting
     * @param event Handle menu item for exit.
     */
    @FXML
    public void handleExit(ActionEvent event) {
        Platform.exit();
    }

    /**
     * Handle the About menu item
     * @param event The menu item event that triggered this listener
     */
    @FXML
    public void handleAbout(ActionEvent event) {
        Window owner = ((javafx.scene.control.MenuItem) event.getSource())
            .getParentPopup().getOwnerWindow();

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("About Spectral Analyzer");
        alert.setHeaderText("Spectral Analyzer v0.1");
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
    @FXML RadioMenuItem radioGrayscale;
    @FXML ComboBox<String> comboColorMap;
    /**
     * Convert the double value to color value
     *
     * @param db The double value
     * @return Interpretted color
     */
    private Color getColorForMagnitude(double db) {
        // Normalize dB (assume range -100 to 0)
        // double normalized = (db + 100) / 100.0;
        double normalized = (db - minDecibel) / (maxDecibel - minDecibel);
        normalized = Math.clamp(normalized, 0.0, 1.0); // Java 21+ clamp

        String comboChoice = (String) comboColorMap.getValue();
        if (comboChoice == null) {
            // if not ready...default to grayscale
            comboChoice = "Grayscale";
        }

        // if (radioGrayscale.isSelected()) {
        switch(comboChoice) {
            case "Grayscale":
                // ================  Grayscale =====================
                // Linearly interpolate from Black (0.0) to White (1.0)
                return Color.BLACK.interpolate(Color.WHITE, normalized);

            case "Heatmap":
                            // Simple "Heat" map: Black -> Blue -> Red -> Yellow
                if (normalized < 0.2) {
                    return Color.BLACK;
                }
                if (normalized < 0.5) {
                    return Color.BLUE.interpolate(Color.RED, (normalized - 0.2) / 0.3);
                }
                return Color.RED.interpolate(Color.YELLOW, (normalized - 0.5) / 0.5);
            default:
                return Color.BLACK.interpolate(Color.WHITE, normalized);
        } // end of switch
    }

    /**
     * Core update method to redraw GUI
     */
    public void updateDisplay() {
        if (sigMfHelper.getDataBuffer() == null) {
            return;
        }

        // Get pixel width
        int canvasW = (int) spectrogramCanvas.getWidth();
        // int canvasH = (int) spectrogramCanvas.getHeight();
        if (canvasW <= 0) {
            return; // Wait for layout
        }

        var global = sigMfHelper.getMetadata().global();
        String dataType = global.datatype();
        int bytesPerSample = global.getBytesPerSample();
        double sampleRate = global.sampleRate();

        // waterfall[Time][Frequency]
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

        // update labels and Rectangles associated with annotations
        updateAnnotationDisplay();

        // selection rectangle
        if (selectionRect != null) {
            double canvasWD = spectrogramCanvas.getWidth();
            long samplesInView = (long)canvasW * fftSize;

            // Calculate new X based on current scroll offset
            double newX = ((double)(selectionStartSample - currentSampleOffset) / samplesInView) * canvasWD;
            double newWidth = (selectionStartWidthSamples / samplesInView) * canvasWD;

            selectionRect.setX(newX);
            selectionRect.setWidth(newWidth);

            // Hide the box if it scrolls completely off screen
            selectionRect.setVisible(newX + newWidth > 0 && newX < canvasW);
        }

        timeLabelsContainer.getChildren().clear();

        // --------------------------------------------------------------
        // Create 5 equidistant points for the time axis
        // --------------------------------------------------------------
        double labelWidth = 60;
        for (int i = 0; i <= 4; i++) {
            double xPixel = (canvasW * 0.25) * i;
            long sampleAtPixel = currentSampleOffset + ((long) (xPixel * fftSize));
            double seconds = (double) sampleAtPixel / sampleRate;

            Line tick = new Line(xPixel, 0, xPixel, 5);
            tick.setStroke(Color.ALICEBLUE);

            Label timeLabel = new Label(String.format(" %.6fs", seconds));
            timeLabel.setPrefWidth(labelWidth);
            timeLabel.setTextFill(Color.LIGHTGRAY);
            timeLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");
            timeLabel.setLayoutX(xPixel - (labelWidth / 2));
            timeLabel.setLayoutY(8); // shift text under the ticks
            timeLabelsContainer.getChildren().addAll(tick, timeLabel);
        }

        // enforce the left/right spacers match the appropriate panels
        leftAxisSpacer.setMinWidth(frequencyRuler.getWidth());
        rightAxisSpacer.setMinWidth(rightPanel.getWidth());

        updateRulers();
        renderSpectrogram(waterfall);
    }

    private void updateRect(Rectangle rect, SigMfAnnotation annot) {
        double canvasW = spectrogramCanvas.getWidth();
        double canvasH = spectrogramCanvas.getHeight();
        double sampleRate = sigMfHelper.getMetadata().global().sampleRate();
        double centerFreq = sigMfHelper.getMetadata().captures().get(0).frequency();


        long offsetInSamples = annot.getSampleStart() - currentSampleOffset;
        double x = (double) offsetInSamples / fftSize;
        double width = (double) annot.getSampleCount() / fftSize;

        // 2. Calculate Vertical (Frequency) Position
        // Map Frequency back to 0.0-1.0 range of the current capture bandwidth
        double bw = sampleRate;
        double fLowRel = (annot.getFreqLowerEdge() - (centerFreq - bw / 2)) / bw;
        double fHighRel = (annot.getFreqUpperEdge() - (centerFreq - bw / 2)) / bw;
        double y = (1.0 - fHighRel) * canvasH;
        double height = (fHighRel - fLowRel) * canvasH;

        // Update rectangle
        rect.setX(x);
        rect.setWidth(width);
        rect.setY(y);
        rect.setHeight(height);
        rect.setVisible(x + width > 0 && x < canvasW);

        // if (!showAnnotationsCheckbox.isSelected()) {
        if (!menuItemShowAnnotations.isSelected()) {
            // keep hidden
            rect.setVisible(false);

        }
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
        rect.setStroke(annotationColorPicker.getValue());
        rect.setStrokeWidth(2);
        rect.setCursor(Cursor.HAND);

        // Create the Label
        // --------------------------------------
        Label label = new Label(cAnnot.getLabel() != null ? cAnnot.getLabel() : "Unknown");
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-background-color: rgba(0,0,0,0.5);");
        label.setPadding(new Insets(2));
        label.setMouseTransparent(true); // So the label doesn't block clicking the box

        // set up the tool tip
        String tooltipText = String.format("Label: %s%nComment: %s",
                            cAnnot.getLabel() != null ? cAnnot.getLabel() : "N/A",
                            cAnnot.getComment() != null ? cAnnot.getComment() : "No comment");

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: #333; -fx-text-fill: #2ecc71;");
        tooltip.setShowDelay(javafx.util.Duration.millis(300)); // Show quickly
        Tooltip.install(rect, tooltip);

        // Store the association
        // --------------------------------------
        AnnotationGroup group = new AnnotationGroup();
        group.rect = rect;
        group.label = label;
        group.data = cAnnot;
        group.tooltip = tooltip;
        annotationMap.put(rect, group);

        annotationOverlay.getChildren().addAll(rect, label);

        rect.setOnMouseClicked(e -> {
            // do not let event bubble up to the pane
            e.consume();

            if (e.getButton() == MouseButton.SECONDARY) {
                // NOTE: Support removing selected annotation
                //       with the right mouse button.
                annotationOverlay.getChildren().remove(rect);
                removeAnnotation(rect);

                // disable the selectionAnnotaiton and button for analyze selection
                selectionAnnotation = null;
                btnAnalyzeSelection.setDisable(true);

            } else {
                // Note: update selection to point to the current annotation
                resetSelection();

                // Assign selected annotation to the one being clicked on.
                selectionAnnotation = cAnnot;
                btnAnalyzeSelection.setDisable(false);

                // prepare current annotation for analysis
                selectionStartSample = cAnnot.getSampleStart();
                selectionStartWidthSamples = cAnnot.getSampleCount();
                selectionFreqLow =  cAnnot.getFreqLowerEdge();
                selectionFreqHigh = cAnnot.getFreqUpperEdge();

                updateRect(selectionRect, selectionAnnotation);
                selectionRect.setVisible(true);

                //---------------  update UI  ------------------------
                selectionNameField.setText(cAnnot.getLabel());
                selectionDescField.setText(cAnnot.getComment());
                selectionComplete = true;
                lblFreqLow.setText(
                    String.format("%.6f MHz", cAnnot.getFreqLowerEdge() / 1e6));
                lblFreqHigh.setText(
                    String.format("%.6f MHz", cAnnot.getFreqUpperEdge() / 1e6));
                lblSelectionStart.setText(String.format("%d samples", cAnnot.getSampleStart()));
                lblSelectionDur.setText(String.format("%d samples", cAnnot.getSampleCount()));
                updateDisplay();
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
            // update rect (x,y,width, height) based on annotation information
            updateRect(rect, group.data);

            // NOTE: check for custom color provided the label for this annotation
            if (annotationStyles.containsKey(group.label.getText())) {
                Color styleColor = annotationStyles.get(group.label.getText());
                rect.setStroke(styleColor);
                rect.setFill(styleColor.deriveColor(0, 1, 1, 0.3)); // apply 0.3 alpha to the fill
            } else {
                rect.setStroke(annotationColorPicker.getValue());
                rect.setFill(annotationColorPicker.getValue().deriveColor(0, 1, 1, 0.3)); // apply 0.3 alpha to the fill
            }

            // update labels and comment
            group.label.setText(group.data.getLabel());
            group.tooltip.setText(group.data.getComment());

            // Position the label at the top-left of the rectangle
            group.label.setLayoutX(rect.getX());
            group.label.setLayoutY(rect.getY() - 20); // Position slightly above the box

            // Match visibility
            group.label.setVisible(rect.isVisible());

        });
    }

    @FXML
    private void handleColorMapChange(ActionEvent e) {
        updateDisplay(); // redraw for the new color.
    }

    /**
     * Handle opening the dialog to map a label to a custom color scheme
     */
    @FXML
    private void handleOpenColorDialog(ActionEvent event) {
        try {
            // Use your FxWeaver or FXMLLoader as before
            FXMLLoader loader = new FXMLLoader(getClass().getResource("annotation-style-dialog.fxml"));
            Parent content = loader.load();

            AnnotationStyleDialogController controller = loader.getController();

            // Pass the CURRENT map to the controller to populate its TableView
            controller.setStyles(annotationStyles);

            Dialog<Map<String, Color>> dialog = new Dialog<>();
            Window owner = ((javafx.scene.control.MenuItem) event.getSource())
                .getParentPopup().getOwnerWindow();
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("Custom Annotation Styles");
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
            MC_LOGGER.error("Failed to open styles dialog", e);
        }
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
        int fftSize = waterfallData[0].length;
        double fs = sigMfHelper.getMetadata().global().sampleRate();

        // NOTE: waterfallData uses Common Math FFT with no normalization
        //       that scales by 1/fftSize**2
        //       Also convert from dB/bin -> dB/Hz
        double binBandwidth = fs / fftSize;
        double conversion = 10 * Math.log10(binBandwidth) + 20 * Math.log10(fftSize);

        for (int t = 0; t < canvasW; t++) {
            for (int f = 0; f < canvasH; f++) {
                // Map Canvas Y pixel to FFT Bin
                // We scale the fftSize down to the canvasH
                int fftBinIndex = (int) ((double) f / canvasH * fftSize);

                // NOTE: trying to compensate so threshold is not dependent of NFFT
                double db = waterfallData[t][fftBinIndex] - conversion;
                // double db = waterfallData[t][fftBinIndex];
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
        if (sigMfHelper.getMetadata() == null) {
            return;
        }

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
     * Show credits to third-party libraries
     */
    @FXML
    private void handleShowCredits(ActionEvent event) {
        try (InputStream is = getClass().getResourceAsStream(
        "/net/kcundercover/spectral_analyzer/ThirdPartyNotices.txt")) {

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);


            // Display in a scrollable text area
            TextArea textArea = new TextArea(content);
            textArea.setEditable(false);
            textArea.setFont(Font.font("Consolas", 12));
            textArea.setWrapText(true);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            Window owner = ((javafx.scene.control.MenuItem) event.getSource())
                .getParentPopup().getOwnerWindow();
            alert.initOwner(owner);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.setTitle("Third-Party Notices");
            alert.setHeaderText("Legal & Attribution Information");
            alert.getDialogPane().setContent(textArea);
            alert.setResizable(true);
            alert.showAndWait();

        } catch (Exception e) {
            MC_LOGGER.error("Could not load credits file", e);
        }
    }

    // ============================================================================================
    // REST API Capabilities
    // ============================================================================================

    @FXML
    private void handleConnect(ActionEvent event) {
        Window owner = ((javafx.scene.control.MenuItem) event.getSource())
            .getParentPopup().getOwnerWindow();
        // connect to a REST server.
        TextInputDialog dialog = new TextInputDialog("http://localhost:8000/openapi.json");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Connect to REST Service");
        dialog.setHeaderText("Enter OpenAPI Schema URL");
        dialog.setContentText("Schema URL:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(url -> {
            try {
                // Basic validation check
                URI.create(url);

                // Call your discovery method (should be run on a background thread)
                MC_LOGGER.info("Attempting to connect to: " + url);
                restHelper.discover(url);

            } catch (IllegalArgumentException iae) {
                showErrorAlert("Invalid URL", "The address provided is not a valid URL.");
            } catch (Exception ex) {
                showErrorAlert("Invalid URL",  ex.toString());
            }

        });
    }


    /**
     * Show dialog box for user to select the capability from a choice
     * @param event ActionEvent that trigger this (MenuItem)
     */
    @FXML
    private void showChooseCapability(ActionEvent event) {
        if (!selectionComplete) {
            showErrorAlert(
                "Selection Incomplete",
                "Select time/freq segment or click on annotation");
            return;
        } else if (selectionAnnotation == null){
            showErrorAlert(
                "Select an annotation",
                "Click/Select an annotation first");
            return;
        }

        double inputFs = sigMfHelper.getMetadata().global().sampleRate();
        double inputFc = sigMfHelper.getMetadata().captures().get(0).frequency();

        // =======================================================
        // NOTE: Send a little exta bw and time for PSD analysis
        // =======================================================
        double currBw = (selectionFreqHigh - selectionFreqLow);
        double center = (selectionFreqHigh + selectionFreqLow) / 2.0 - inputFc;

        // NOTE: extend time by
        long targetStart = selectionStartSample;
        long targetWidth = (long) selectionStartWidthSamples;
        int down = (int) Math.ceil(inputFs / currBw);
        double targetFs = inputFs / down;

        final double finalTargetFs = targetFs;
        // final double finalStartTime = targetStart / inputFs;
        String dataType = sigMfHelper.getMetadata().global().datatype();

        asyncDownConvertService.extractAndDownConvertAsync(
                sigMfHelper.getDataBuffer(), targetStart, (int) targetWidth, dataType, center / inputFs, down, fastDownConverter.isSelected())
            .thenAccept(data -> {
            // Build the IqData object
            IqData iqData = new IqData(
                "current", data, finalTargetFs, this.sigMfHelper.getMetadata(), selectionAnnotation);

            // NOTE: return to UI thread
            Platform.runLater(() -> {
                Window owner = ((javafx.scene.control.MenuItem) event.getSource())
                                    .getParentPopup().getOwnerWindow();

                ChoiceDialog<String> dialog = new ChoiceDialog<>();
                dialog.setTitle("Select Capability");

                // NOTE: tie to parent window (so centers dialog)
                dialog.initOwner(owner);
                dialog.initModality(Modality.WINDOW_MODAL);

                dialog.setHeaderText("Choose an API endpoint to execute:");
                dialog.setContentText("Capability:");

                // Fill the ComboBox with the paths from your map
                dialog.getItems().addAll(restHelper.getCapabilityPaths());

                dialog.showAndWait().ifPresent(selectedPath -> {
                    // Retrieve the capability metadata we stored earlier
                    Capability cap = restHelper.getCapability(selectedPath);
                    restHelper.showCapabilityDialog(owner, cap, iqData);
                });
            });
        })
        .exceptionally(ex -> {
            Platform.runLater(() -> showErrorAlert("DSP Error", ex.getMessage()));
            return null;
        });
    }
}
