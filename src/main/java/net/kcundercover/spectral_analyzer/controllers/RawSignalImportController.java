package net.kcundercover.spectral_analyzer.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import net.kcundercover.spectral_analyzer.data.RawSignalImportSettings;

public class RawSignalImportController {

    @FXML private ComboBox<String> datatypeCombo;
    @FXML private TextField sampleRateField;
    @FXML private TextField centerFreqField;
    @FXML private TextField timestampField;

    @FXML
    public void initialize() {
        datatypeCombo.getItems().addAll("cf32_le", "cf32_be", "ci16_le", "ci16_be", "cf64_le", "ci8", "cu8");

    }
    public void populateDefaults(File file) {
        datatypeCombo.setValue(detectDefaultDatatype(file.getName()));
        timestampField.setText(getFileTimestamp(file));
    }

    public RawSignalImportSettings getSettings() {
        return new RawSignalImportSettings(
            datatypeCombo.getValue(),
            Double.parseDouble(sampleRateField.getText().trim()),
            Long.parseLong(centerFreqField.getText().trim()),
            timestampField.getText().trim()
        );
    }

    public boolean isInputValid() {
        return isValidDouble(sampleRateField.getText()) && isValidLong(centerFreqField.getText());
    }

    public javafx.beans.value.ObservableValue<String> sampleRateTextProperty() {
        return sampleRateField.textProperty();
    }

    public javafx.beans.value.ObservableValue<String> centerFreqTextProperty() {
        return centerFreqField.textProperty();
    }

    /**
     * Detect the data type based on file extension
     * @param filename Input data file
     * @return Expected data type string for SigMF metadata
     */
    private String detectDefaultDatatype(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".cs16") || lowerName.endsWith(".ci16")) {
            return "ci16_le";
        } else if (lowerName.endsWith(".cf32")) {
            return "cf32_le";
        } else if (lowerName.endsWith(".cf64")) {
            return "cf64_le";
        } else if(lowerName.endsWith(".ci8")) {
            return "ci8";
        } else if (lowerName.endsWith(".cu8")) {
            return "cu8";
        }
        return "cf32_le";
    }

    /**
     * Get the file time stamp of las modification or creation time (if modification time is not available)
     * @param file Data file to analyze for timestamp
     * @return The timestamp of the file in ISO_INSTANT format, or current time if there is an error accessing the file attributes
     */
    private String getFileTimestamp(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            Instant timeInstance = attrs.creationTime().toInstant();
            if (timeInstance.equals(Instant.EPOCH)) {
                timeInstance = attrs.lastModifiedTime().toInstant();
            }
            return DateTimeFormatter.ISO_INSTANT.format(timeInstance);
        } catch (Exception e) {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        }
    }

    private boolean isValidDouble(String text) {
        try {
            Double.parseDouble(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidLong(String text) {
        try {
            Long.parseLong(text.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
