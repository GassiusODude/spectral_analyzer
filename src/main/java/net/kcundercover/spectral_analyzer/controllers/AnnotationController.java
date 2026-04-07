package net.kcundercover.spectral_analyzer.controllers;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Control;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Window;


import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import net.kcundercover.spectral_analyzer.data.AnnotationGroup;
import net.kcundercover.spectral_analyzer.data.AnnotationRow;
import net.kcundercover.spectral_analyzer.data.IqData;
import net.kcundercover.spectral_analyzer.rest.Capability;
import net.kcundercover.spectral_analyzer.data.AnnotationRow;
// import net.kcundercover.spectral_analyzer.rest.CapabilityConfig;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;
import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.sigmf.SigMfHelper;
import net.kcundercover.spectral_analyzer.services.AsyncExtractDownConvertService;
import net.kcundercover.spectral_analyzer.rest.RestHelper;

@Component
public class AnnotationController {
    private static final Logger AC_LOGGER = LoggerFactory.getLogger(AnnotationController.class);
    @FXML private TableView<AnnotationRow> annotationTable;
    @FXML private TableColumn<AnnotationRow, Boolean> selectCol;
    @FXML private TableColumn<AnnotationRow, String> labelCol;
    @FXML private TableColumn<AnnotationRow, String> descCol;
    @FXML private TableColumn<AnnotationRow, Double> startCol;
    @FXML private TableColumn<AnnotationRow, Double> centerFreqCol;
    @FXML private TableColumn<AnnotationRow, Double> durationCol;
    @FXML private TableColumn<AnnotationRow, Double> bandwidthCol;
    private boolean noneSelected;
    private double sampleRate;
    private RestHelper restHelper;
    private SigMfHelper sigmfHelper;

    @Autowired private AsyncExtractDownConvertService asyncDownConvertService;


    @FXML
    public void initialize() {
        annotationTable.setEditable(true);
        labelCol.setCellFactory(TextFieldTableCell.forTableColumn());
        labelCol.setCellValueFactory(cellData ->
            new ReadOnlyStringWrapper(cellData.getValue().getLabel()));

        descCol.setCellValueFactory(cellData ->
            new ReadOnlyStringWrapper(cellData.getValue().getComment()));
                // NOTE: set so edit supports multiline comment
                descCol.setCellFactory(tc -> {
            return new TableCell<AnnotationRow, String>() {
                private final Text text = new Text();
                private final TextArea textArea = new TextArea();

                {
                    // Set up the viewing mode (Text)
                    text.wrappingWidthProperty().bind(descCol.widthProperty().subtract(10));
                    setPrefHeight(Control.USE_COMPUTED_SIZE);

                    // Set up the editing mode (TextArea)
                    textArea.setWrapText(true);

                    // NOTE: save update if text are loses focus
                    textArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                        if (!isFocused && isEditing()) {
                            commitEdit(textArea.getText());
                        }
                    });

                }

                @Override
                public void startEdit() {
                    super.startEdit();
                    textArea.setText(getItem());
                    setGraphic(textArea);
                    setText(null);
                }

                @Override
                public void cancelEdit() {
                    super.cancelEdit();
                    setGraphic(text);
                }

                @Override
                public void commitEdit(String newValue) {
                    super.commitEdit(newValue);
                    // Push the change back to the actual model object
                    AnnotationRow row = getTableView().getItems().get(getIndex());
                    row.setComment(newValue);
                    setGraphic(text);
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        if (isEditing()) {
                            setGraphic(textArea);
                        } else {
                            text.setText(item);
                            setGraphic(text);
                        }
                    }
                }
            };
        });

        // If startTimeProperty() was renamed to getStartTime()
        startCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getStartTime()));

        durationCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getDuration()));

        centerFreqCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getCenterFreq()));

        bandwidthCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getBandwidth()));

        selectCol.setEditable(true);
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
    }

    /**
     * Entrypoint to using the AnnotationController
     *
     * This is the function to pass in the annotations of interest.
     *
     * @param map Map of GUI rect -> AnnotationGroup (from MainController.java)
     * @param sampleRate Sample rate of the signal
     */
    public void configAnnotationController(Map<Rectangle, AnnotationGroup> map, double sampleRate, RestHelper restHelper, SigMfHelper sigmfHelper) {
        this.noneSelected = true;
        this.sampleRate = sampleRate;
        this.restHelper = restHelper;
        this.sigmfHelper = sigmfHelper;
        AC_LOGGER.debug("Initialized noneSelected = " + noneSelected
            + " and sampleRate = " + sampleRate);
        ObservableList<AnnotationRow> rows = FXCollections.observableArrayList();

        for (AnnotationGroup group : map.values()) {
            SigMfAnnotation data = group.data;

            // Derive values from SigMfAnnotation
            double start = data.sampleStart() / sampleRate;
            double duration = data.getSampleCount() / sampleRate;
            double freqLow = data.getFreqLowerEdge().doubleValue();
            double freqHigh = data.getFreqUpperEdge().doubleValue();
            double centerFreq = 0.5 * (freqLow + freqHigh);
            double bandwidth = freqHigh - freqLow;

            rows.add(new AnnotationRow(
                data.getLabel(),
                data.getComment(),
                start, duration, centerFreq, bandwidth,
                group
            ));
        }

        annotationTable.setItems(rows);
    }


    /**
     * Update the annotations loaded when initiating this dialog with
     * modifications to the data in the tables.
     */
    public void updateMainAnnotations() {
        // Access the TableView's items directly
        ObservableList<AnnotationRow> tableRows = annotationTable.getItems();

        for (AnnotationRow row : tableRows) {
            AnnotationGroup group = row.getAssociatedGroup();

            // access the associated SigMF Annotation
            SigMfAnnotation data = group.data;

            // Update the SigMF label and comments
            data.setLabel(row.getLabel());
            group.label.setText(row.getLabel()); // update GUI label
            data.setComment(row.getComment());

            // Update frequency (based on changes to center frequency)
            double freqLow = row.getCenterFreq() - (row.getBandwidth() / 2.0);
            double freqHigh = row.getCenterFreq() + (row.getBandwidth() / 2.0);
            data.setFreqLowerEdge(freqLow);
            data.setFreqUpperEdge(freqHigh);

            // NOTE: MainContoller.java will update the GUI for displaying updates.
        }
    }

    public int getNumSelected() {
        int numSelected = 0;
        for (AnnotationRow row : annotationTable.getItems()) {
            if (row.isSelected()) {
                numSelected++;
            }

        }
        return numSelected;
    }
    @FXML
    private void handleSelectAll() {
        int numRows = 0;
        for (AnnotationRow row : annotationTable.getItems()) {
            row.setSelected(true);
            numRows++;
        }
        if (numRows > 0) {
            noneSelected = false;
        } else {
            // no entries to select
            noneSelected = true;
        }
    }

    @FXML
    private void handleDeselectAll() {
        for (AnnotationRow row : annotationTable.getItems()) {
            row.setSelected(false);
        }
        this.noneSelected = true;
    }

    @FXML
    public void handleConnect(ActionEvent event) {

    }

    public void executeCapability(Capability cap) {
        // int numSelected = getNumSelected();

        // CapabilityConfig cc = new CapabilityConfig(cap);
        AC_LOGGER.info("Execute capability " + cap.getPath());
        for (AnnotationRow row : annotationTable.getItems()) {
            if (row.isSelected()) {
                AC_LOGGER.info("Excute capability (%s) for %s at %f seconds",
                    cap.getPath(), row.getLabel(), row.getStartTime());

                int down = (int) Math.floor(this.sampleRate / row.getBandwidth());
                double targetFs = this.sampleRate / down;
                long targetStart = (long)(row.getStartTime() * this.sampleRate);
                long targetDur = (long)(row.getDuration() * this.sampleRate);
                String dataType = sigmfHelper.getMetadata().global().datatype();
                double inputFc = sigmfHelper.getMetadata().captures().get(0).frequency();
                double center = row.getCenterFreq() - inputFc;
                asyncDownConvertService.extractAndDownConvertAsync(
                        sigmfHelper.getDataBuffer(), targetStart, (int) targetDur, dataType, center / this.sampleRate, down, false)
                    .thenAccept(data -> {
                        // downsample the burst
                        IqData iqData = new IqData(
                            "current", data, targetFs, sigmfHelper.getMetadata(), row.getAssociatedGroup().data);

                        // TODO:
                    });
            }

        }
    }

    @FXML
    public void showChooseCapability(ActionEvent event) {
        Window owner = ((javafx.scene.control.MenuItem) event.getSource())
                        .getParentPopup().getOwnerWindow();
        Platform.runLater(() -> {
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
                AC_LOGGER.info("Selected " + cap.getBaseUrl() + cap.getPath());

                executeCapability(cap);
            });
        });
    }


}
