package net.kcundercover.spectral_analyzer;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spectral Analyzer JavaFxApplication
 */
@SpringBootApplication
public class SpectralAnalyzerApplication {

    /**
     * Default constructor
     */
    public SpectralAnalyzerApplication() {

    }

    /**
     * Main JavaFXApplication function
     * @param args Input arguments
     */
    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
