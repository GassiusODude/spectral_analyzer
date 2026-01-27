package net.kcundercover.spectral_analyzer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(classes = SpectralAnalyzerApplication.class)
class SpectralAnalyzerApplicationTests {

    @BeforeAll
    static void setupHeadless() {
        // Tells Java and JavaFX to run without a physical screen
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
    }

    @Test
    void contextLoads() {
        // This will now pass as Spring starts without trying to open a window
    }
}
