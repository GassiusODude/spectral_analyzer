package net.kcundercover.spectral_analyzer;

import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.stereotype.Component;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.event.ActionEvent;

@Component
@FxmlView("main-scene.fxml") // Links to the XML file above
public class MainController {

    @FXML
    public void handleOpen(ActionEvent event) {
        System.out.println("Opening Signal File...");
    }

    @FXML
    public void handleBeatDetection(ActionEvent event) {
        System.out.println("Running ODF Beat Detection...");
    }

    @FXML
    public void handleExit(ActionEvent event) {
        Platform.exit();
    }
}
