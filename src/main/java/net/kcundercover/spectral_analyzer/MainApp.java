package net.kcundercover.spectral_analyzer;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MainApp {
    public static void main(String[] args) {
        // This launches the JavaFX Application lifecycle
        Application.launch(JavaFxApplication.class, args);
    }
}
