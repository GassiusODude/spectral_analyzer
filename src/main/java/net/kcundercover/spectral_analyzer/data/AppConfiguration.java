package net.kcundercover.spectral_analyzer.data;

import lombok.Data;

/**
 * Data model representing the user settings for the Spectral Analyzer.
 * Default values are used if no config.json exists in AppData.
 */
@Data
public class AppConfiguration {

    // --- UI/Visual Configurations ---
    private String colorMap = "Grayscale";                 // Options: "Grayscale", "Heatmap"
    private double windowWidth = 600.0;
    private double windowHeight = 400.0;
    private boolean showGridLines = true;

    // --- Signal Processing Configurations ---
    private int fftPower = 10;             // Options: 1024, 2048, 4096, 8096

    // --- File System Configurations ---
    private String lastUsedDataDirectory = "";     // Paths to source raw signal files
    private String selectColor = "#00FF00FF";
    private String annotColor = "#FF00FFFF";
}
