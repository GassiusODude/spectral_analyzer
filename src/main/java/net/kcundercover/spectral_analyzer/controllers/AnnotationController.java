package net.kcundercover.spectral_analyzer.controllers;



import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.shape.Rectangle;
import java.util.Map;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import net.kcundercover.spectral_analyzer.data.AnnotationGroup;
import net.kcundercover.spectral_analyzer.data.AnnotationRow;
import net.kcundercover.spectral_analyzer.sigmf.SigMfAnnotation;

public class AnnotationController {
    @FXML private TableView<AnnotationRow> annotationTable;
    @FXML private TableColumn<AnnotationRow, String> labelCol;
    @FXML private TableColumn<AnnotationRow, String> descCol;
    @FXML private TableColumn<AnnotationRow, Double> startCol;
    @FXML private TableColumn<AnnotationRow, Double> centerFreqCol;
    @FXML private TableColumn<AnnotationRow, Double> durationCol;
    @FXML private TableColumn<AnnotationRow, Double> bandwidthCol;

    @FXML
    public void initialize() {

        labelCol.setCellValueFactory(cellData ->
            new ReadOnlyStringWrapper(cellData.getValue().getLabel()));

        descCol.setCellValueFactory(cellData ->
            new ReadOnlyStringWrapper(cellData.getValue().getComment()));

        // If startTimeProperty() was renamed to getStartTime()
        startCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getStartTime()));

        durationCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getDuration()));

        centerFreqCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getCenterFreq()));

        bandwidthCol.setCellValueFactory(cellData ->
            new ReadOnlyObjectWrapper<>(cellData.getValue().getBandwidth()));


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
                start,
                duration,
                centerFreq,
                bandwidth
            ));
        }

        annotationTable.setItems(rows);
    }

}
