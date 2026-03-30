package net.kcundercover.spectral_analyzer.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.shape.Rectangle;
import java.util.Map;

import javafx.scene.control.Control;
import javafx.scene.control.TableCell;
import javafx.scene.text.Text;


import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import net.kcundercover.spectral_analyzer.data.AnnotationGroup;
import net.kcundercover.spectral_analyzer.data.AnnotationRow;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;

public class AnnotationController {
    @FXML private TableView<AnnotationRow> annotationTable;
    @FXML private TableColumn<AnnotationRow, Boolean> selectCol;
    @FXML private TableColumn<AnnotationRow, String> labelCol;
    @FXML private TableColumn<AnnotationRow, String> descCol;
    @FXML private TableColumn<AnnotationRow, Double> startCol;
    @FXML private TableColumn<AnnotationRow, Double> centerFreqCol;
    @FXML private TableColumn<AnnotationRow, Double> durationCol;
    @FXML private TableColumn<AnnotationRow, Double> bandwidthCol;

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

        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
    }

    public void setAnnotations(Map<Rectangle, AnnotationGroup> map, double sampleRate) {
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


    public void updateMainAnnotations(double sampleRate) {
        // Access the TableView's items directly
        ObservableList<AnnotationRow> tableRows = annotationTable.getItems();

        for (AnnotationRow row : tableRows) {
            AnnotationGroup group = row.getAssociatedGroup();
            SigMfAnnotation data = group.data;

            // 1. Update the Metadata Object
            data.setLabel(row.getLabel());
            data.setComment(row.getComment());

            // 2. Reverse the math for Frequency/Time
            double freqLow = row.getCenterFreq() - (row.getBandwidth() / 2.0);
            double freqHigh = row.getCenterFreq() + (row.getBandwidth() / 2.0);

            // Update the SigMF object's frequency fields if they exist
            // data.setFrequencyLow(freqLow);
            // data.setFrequencyHigh(freqHigh);

            // 3. Update the UI components
            group.label.setText(row.getLabel());

            // Optional: If the rectangle needs to move/resize based on table edits:
            // updateRectangle(group.rect, row.getStartTime(), row.getDuration(), freqLow, freqHigh);
        }
    }


}
