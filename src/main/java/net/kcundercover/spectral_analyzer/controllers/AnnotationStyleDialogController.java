package net.kcundercover.spectral_analyzer.controllers;



import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import net.kcundercover.spectral_analyzer.data.AnnotationStyle;

@Component
public class AnnotationStyleDialogController {
    private static final Logger ASDC_LOGGER = LoggerFactory.getLogger(AnnotationStyleDialogController.class);
    @FXML private TableView<AnnotationStyle> table;
    @FXML private TableColumn<AnnotationStyle, String> keyCol;
    @FXML private TableColumn<AnnotationStyle, Color> colorCol;
    @FXML private TextField labelField;
    @FXML private ColorPicker colorPicker;
    @FXML private Button addBtn;
    @FXML private Button removeBtn;
    @FXML private Button loadBtn;
    @FXML private Button saveBtn;

    // The list of annotations
    private final ObservableList<AnnotationStyle> styleList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // tie remove button to the selectedItemProperty of the table
        removeBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        // NOTE: on selection in table, update name and color editor
        //       If same name, will overwrite the color associated.
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                labelField.setText(newVal.label());
                colorPicker.setValue(newVal.color());
            }
        });

        // ======================================
        // Label column
        // ======================================
        keyCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().label()));

        // ======================================
        // Set up colo column
        // ======================================
        colorCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().color()));
        colorCol.setCellFactory(column -> new TableCell<AnnotationStyle, Color>() {
                private final javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(40, 15);

                @Override
                protected void updateItem(Color item, boolean empty) {
                    super.updateItem(item, empty);

                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        // Set the rectangle color to the actual color from the ColorPicker
                        rect.setFill(item);
                        rect.setStroke(Color.BLACK);
                        rect.setArcWidth(5);
                        rect.setArcHeight(5);

                        // show graphic color rect instead of hexstring
                        setGraphic(rect);
                        setText(null);

                        setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                        setAlignment(javafx.geometry.Pos.CENTER);
                    }
                }
            });

        // Set the list as the data source
        table.setItems(styleList);

    }


    /**
     * Extract the current style list from this controller
     *
     * @return Called to pull the modified list in this controller
     */
    public Map<String, Color> getUpdatedStyles() {
        Map<String, Color> map = new HashMap<>();
        for (AnnotationStyle style : styleList) {
            map.put(style.label(), style.color());
        }
        return map;
    }


    /**
     * Set the styles list
     *
     * Pass the existing styles map into this dialog
     *
     * @param existingStyles A map of existing keys to color mapping.
     */
    public void setStyles(Map<String, Color> existingStyles) {
        styleList.clear();
        existingStyles.forEach((label, color) ->
            styleList.add(new AnnotationStyle(label, color)));
    }

    /**
     * Convert the color to a hexstring for saving this color mapping
     *
     * @param c color
     * @return Hexstring representation of color
     */
    private static String colorToHex(Color c) {
        return String.format(
            "#%02X%02X%02X",
            (int) (c.getRed() * 255),
            (int) (c.getGreen() * 255),
            (int) (c.getBlue() * 255)
        );
    }

    /**
     * Convert hexstring representation to a color, used by load mapping
     *
     * @param hex The hexstring
     * @return The Color object
     */
    private static Color hexToColor(String hex) {
        return Color.web(hex);
    }

    // ============================================================================================
    //                                          button handlers
    // ============================================================================================

    @FXML
    private void handleAddStyle() {
        ASDC_LOGGER.debug("Adding new style for label: {}", labelField.getText());
        // Logic to add labelField.getText() and colorPicker.getValue() to the TableView
        String label = labelField.getText().trim();
        if (label.isEmpty()) {
            return;
        }

        AnnotationStyle newStyle = new AnnotationStyle(label, colorPicker.getValue());

        // Check for duplicates in the list
        styleList.removeIf(s -> s.label().equalsIgnoreCase(label));
        styleList.add(newStyle);

        ASDC_LOGGER.debug("Style list updated. Current count: {}", styleList.size());
    }

    @FXML
    private void handleRemoveStyle() {
        // 1. Get the currently selected item from the table
        AnnotationStyle selectedStyle = table.getSelectionModel().getSelectedItem();

        if (selectedStyle != null) {
            // 2. Remove from the ObservableList
            styleList.remove(selectedStyle);

            ASDC_LOGGER.debug("Removed style for: {}", selectedStyle.label());

            // Optional: Clear the input fields if they match the deleted item
            if (labelField.getText().equals(selectedStyle.label())) {
                labelField.clear();
            }
        } else {
            // 3. Optional: Alert the user if nothing is selected
            ASDC_LOGGER.warn("Remove clicked, but no style was selected in the table.");
        }
    }

    @FXML
    private void handleLoadStyles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Color Map");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File file = fc.showOpenDialog(table.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(file);

            if (!root.isObject()) {
                throw new IllegalArgumentException("JSON root must be an object");
            }

            // NOTE: add entries from the JSON file (appends)
            root.fields().forEachRemaining(e -> {
                String key = e.getKey();
                String hex = e.getValue().asText();
                styleList.add(new AnnotationStyle(key, hexToColor(hex)));
            });

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to load color map", e.getMessage());
        }
    }


    @FXML
    private void handleSaveStyles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Color Map");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        File file = fc.showSaveDialog(table.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            for (AnnotationStyle entry : styleList) {
                root.put(entry.label(), colorToHex(entry.color()));
            }

            mapper.writerWithDefaultPrettyPrinter()
                .writeValue(file, root);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to save color map", e.getMessage());
        }
    }

    /**
     * Helper function to show an error dialog
     * @param title Title of the error dialog
     * @param message The error message to show.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
