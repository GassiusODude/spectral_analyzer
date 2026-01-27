package net.kcundercover.spectral_analyzer;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.kcundercover.spectral_analyzer.SpectralAnalyzerApplication;
import net.rgielen.fxweaver.core.FxWeaver;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        // This is where Spring Boot is actually started
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.context = new SpringApplicationBuilder()
                .sources(SpectralAnalyzerApplication.class)
                .run(args);
    }

    @Override
    public void start(Stage stage) {
        FxWeaver fxWeaver = context.getBean(FxWeaver.class);
        // This loads the FXML and hooks up the Spring-managed Controller
        Parent root = fxWeaver.loadView(MainController.class);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Spectral Analyzer - 2026");
        stage.show();
    }

    @Override
    public void stop() {
        this.context.close();
        Platform.exit();
    }
}
