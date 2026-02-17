package net.kcundercover.spectral_analyzer;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main for the SpringBoot Applications
 */
@SpringBootApplication
public class MainApp {

    /**
     * Default constructor
     */
    public MainApp() {}
    /**
     * Main method for Springboot application
     * @param args Input arguments
     */
    public static void main(String[] args) {
        // This launches the JavaFX Application lifecycle
        Application.launch(JavaFxApplication.class, args);
    }
}
