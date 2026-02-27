package net.kcundercover.spectral_analyzer;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import net.kcundercover.spectral_analyzer.SpectralAnalyzerApplication;
import net.rgielen.fxweaver.core.FxWeaver;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import net.kcundercover.spectral_analyzer.controllers.MainController;

/**
 * JavaFX application
 */
public class JavaFxApplication extends Application {

    /** Default constructor */
    public JavaFxApplication() {}
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
        Stage splashStage = new Stage(StageStyle.UNDECORATED);
        ImageView splashImage = new ImageView(new Image("/icons/spectral_analyzer.png"));
        splashImage.setFitWidth(600);
        splashImage.setPreserveRatio(true);
        splashImage.setSmooth(true);
        VBox rootVBox = new VBox(splashImage);
        splashStage.setScene(new Scene(rootVBox, 600, 600));
        splashStage.show();



        FxWeaver fxWeaver = context.getBean(FxWeaver.class);
        // This loads the FXML and hooks up the Spring-managed Controller
        Parent root = fxWeaver.loadView(MainController.class);

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("Spectral Analyzer - 2026");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/spectral_analyzer.png")));
        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(event -> {
            splashStage.hide();
            stage.show();


        });
        delay.play();
    }

    @Override
    public void stop() {
        this.context.close();
        Platform.exit();
    }
}
