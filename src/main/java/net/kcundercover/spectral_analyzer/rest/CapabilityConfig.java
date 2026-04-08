package net.kcundercover.spectral_analyzer.rest;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.util.converter.DoubleStringConverter;

import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import net.kcundercover.spectral_analyzer.data.IqData;
import net.kcundercover.spectral_analyzer.rest.Capability;
// import net.kcundercover.spectral_analyzer.rest.RestHelper;

public class CapabilityConfig {
    private static final Logger CC_LOGGER = LoggerFactory.getLogger(CapabilityConfig.class);
    private Capability cap;
    private IqData iqData;
    GridPane grid;
    Map<String, CapabilityUI> inputFields;

    public CapabilityConfig(Capability cap, IqData iqData) {
        this.cap = cap;
        this.iqData = iqData;
        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        inputFields = new HashMap<>();
        buildFormFromSchema(iqData);
    }

    @SuppressFBWarnings
    public Map<String, CapabilityUI> getInputFields() {
        return inputFields;
    }

    /**
     * Check if the object type matches the schema type
     * @param value The object value
     * @param schemaType The data type described in the schema
     * @return Return true if compatible type to schema
     */
    private boolean isTypeCompatible(Object value, String schemaType) {
        if (value == null) {
            return false;
        }
        return switch (schemaType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number; // Covers Double, Float, Integer, etc.
            case "boolean" -> value instanceof Boolean;
            case "array" -> value.getClass().isArray() || value instanceof java.util.Collection;
            default -> false;
        };
    }

    /**
     * Update the control with the specified value
     * @param control The GUI control component
     * @param value The data object associated.
     */
    private void updateControlValue(Control control, Object value) {
        if (value == null) {
            return;
        }
        String strValue;
        if (value.getClass().isArray()) {
            strValue = java.util.Arrays.deepToString((Object[]) value);
        } else {
            strValue = value.toString();
        }
        if (control instanceof TextField tf) {
            tf.setText(strValue);
        } else if (control instanceof Spinner spinner) {
            // We use a helper to set the value safely
            spinner.getValueFactory().setValue(value);
        } else if (control instanceof CheckBox cb && value instanceof Boolean b) {
            cb.setSelected(b);
        } else if (control instanceof ComboBox combo) {
            // If the main input is also a combo (for enums)
            combo.setValue(value.toString());
        }
    }

    /**
     * Design UI form based on schemaProperties
     * @param schemaProperties Schema properties
     * @param grid Gridpane for the dynamic GUI
     * @param inputFields GUI components to take in input
     * @param iq IQ data and SigMF properties
     */
    public void buildFormFromSchema(IqData iq) {
        JsonNode schemaProperties = cap.getSchema();
        int row = 0;

        Map<String, Object> dataMap = iq.getData();
        Map<String, Object> dataMapBuffer = iq.getDataBuffer();
        for (var entry : schemaProperties.properties()) {
            String propertyName = entry.getKey();
            JsonNode propertyDetails = entry.getValue();
            String type = propertyDetails.path("type").asText();

            grid.add(new Label(propertyName + ":"), 0, row);

            // initialize a control UI and a combo box with similiar data types
            Control inputControl;
            ComboBox<String> comboData = new ComboBox<>();
            TextField selectTF = new TextField();

            // ----------------------------------------------------------------
            // Prepare the input control UI in current row
            // ----------------------------------------------------------------
            if ("buffer".equals(type)) {
                // NOTE: buffer type, get from IQ data
                ComboBox<String> combo = new ComboBox<>();
                for (Map.Entry<String, Object> dataEntry : dataMapBuffer.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();

                    // Only add if the value is actually a binary format
                    if (value instanceof byte[] || value instanceof java.nio.ByteBuffer) {
                        combo.getItems().add(key);
                    }
                }
                inputControl = combo;

            } else if (propertyDetails.has("enum")) {
                CC_LOGGER.trace("Enum for " + propertyName + ":\n" + propertyDetails.toPrettyString());
                ComboBox<String> combo = new ComboBox<>();
                propertyDetails.get("enum").forEach(val -> combo.getItems().add(val.asText()));
                inputControl = combo;
            } else if ("integer".equals(type)) {
                // Spinner for integers: Min, Max, Default
                int min = propertyDetails.path("minimum").asInt(0);
                int max = propertyDetails.path("maximum").asInt(Integer.MAX_VALUE);
                int defaultValue = propertyDetails.path("default").asInt(0);
                inputControl = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    min, max, defaultValue));
                ((Spinner<?>) inputControl).setEditable(true);
            } else if ("number".equals(type)) {
                TextField inputField = new TextField();

                double defaultValue = propertyDetails.path("default").asDouble(0.0);
                inputField.setText(String.valueOf(defaultValue));

                TextFormatter<Double> formatter = new TextFormatter<>(new DoubleStringConverter(), defaultValue);
                inputField.setTextFormatter(formatter);

                // Access the value safely later
                Double finalValue = formatter.getValue();
                inputControl = inputField;

            } else if ("boolean".equals(type)) {
                inputControl = new CheckBox();
            } else {
                inputControl = new TextField();
                ((TextField)inputControl).setPromptText(type);
            }
            CC_LOGGER.trace("Property = " + propertyName + "\t(Type = " + type + ")");

            // ----------------------------------------------------------------
            // Prepare combo box for simple input in this row
            // ----------------------------------------------------------------
            if ("buffer".equals(type)) {
                for (Map.Entry<String, Object> dataEntry: dataMapBuffer.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    comboData.getItems().add(key);
                }
            } else {
                // update the keys to properties with matching data types
                for (Map.Entry<String, Object> dataEntry: dataMap.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    if (isTypeCompatible(value, type)) {
                        comboData.getItems().add(key);
                    }
                }
            }

            // add selection listener to update control if selection is made
            comboData.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    Object sourceValue = dataMap.get(newVal);
                    updateControlValue(inputControl, sourceValue);
                    updateControlValue(selectTF, newVal);
                    // selectedText = newVal;
                }
            });

            // ----------------------------------------------------------------
            // update grid
            // ----------------------------------------------------------------
            grid.add(inputControl, 1, row);

            // NOTE: check "buffer" or "enum",
            //       both use ComboBox for inputField  already
            if (!("buffer".equals(type) || propertyDetails.has("enum")) ) {
                grid.add(comboData, 2, row);
            }
            inputFields.put(propertyName, new CapabilityUI(inputControl, selectTF));
            row++;
        }
    }

    public void updateConfig(Map<String, Object> configTemplate, IqData iqData) {
        Map<String, Object> iqMeta = iqData.getData();

        for (Map.Entry<String, CapabilityUI> entry : inputFields.entrySet()) {
            String key = entry.getKey();
            CapabilityUI capUI = entry.getValue();
            Control control = capUI.control;
            String select = capUI.iqDataField.getText();
            if (!select.equals("")) {
                configTemplate.put(key, iqMeta.get(select));
                CC_LOGGER.info(key + " = (" + select + ") from new iqData");

            } else {
                CC_LOGGER.info(key + " = " + configTemplate.get(key));
            }

        }
    }
    public Map<String, Object> configureCapability(Window owner, IqData iqData) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Execute Capability");
        dialog.setHeaderText("Enter required parameters:");

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                Map<String, Object> results = new HashMap<>();
                inputFields.forEach((name, capConfig) -> {
                    Control control = capConfig.control;
                    String select = capConfig.iqDataField.getText();
                    CC_LOGGER.info("For " + name + " selection = " + select);
                    String schemaType = cap.getSchema().path("properties").path(name).path("type").asText();
                    if (control instanceof TextField) {
                        results.put(name, ((TextField) control).getText());
                    } else if (control instanceof ComboBox) {
                        results.put(name, ((ComboBox<?>) control).getValue());
                    } else if (control instanceof Spinner) {
                        // Extracts the numeric value (Integer or Double)
                        results.put(name, ((Spinner<?>) control).getValue());
                    } else if (control instanceof CheckBox) {
                        // Extracts the boolean true/false
                        results.put(name, ((CheckBox) control).isSelected());
                    }
                });
                return results;
            }
            return null;
        });
        // dialog.showAndWait().ifPresent(inputs -> {
        //     //executeCapability(owner, cap, inputs, iq);
        // });
        return dialog.showAndWait().orElse(null);
    }
}

class CapabilityUI {
    public CapabilityUI(Control control, TextField iqDataField) {
        this.control = control;
        this.iqDataField = iqDataField;
    }
    public Control control;
    public TextField iqDataField;
}
