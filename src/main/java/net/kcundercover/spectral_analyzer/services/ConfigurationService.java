package net.kcundercover.spectral_analyzer.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import net.kcundercover.spectral_analyzer.data.SecureAppStorage;
import net.kcundercover.spectral_analyzer.data.AppConfiguration;

@Service
public class ConfigurationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppConfiguration activeConfig;

    @PostConstruct
    public void init() {
        // Fetch the raw JSON string from AppData
        String rawJson = SecureAppStorage.loadAppConfig();

        if (rawJson == null || rawJson.isEmpty()) {
            System.out.println("No config.json found. Keeping app default settings.");
            this.activeConfig = new AppConfiguration(); // Instantiates your default settings
        } else {
            try {
                // Jackson overwrites defaults with the user's saved choices
                this.activeConfig = objectMapper.readValue(rawJson, AppConfiguration.class);
                System.out.println("User configuration successfully loaded from AppData.");
            } catch (Exception e) {
                System.err.println("Config file corrupted! Falling back to defaults: " + e.getMessage());
                this.activeConfig = new AppConfiguration();
            }
        }
    }

    public AppConfiguration getActiveConfig() {
        if (this.activeConfig == null) {
            return new AppConfiguration();
        }
        try {
            // Unrolls the object to a string and rebuilds it as a fresh copy in memory
            String jsonClone = objectMapper.writeValueAsString(this.activeConfig);
            return objectMapper.readValue(jsonClone, AppConfiguration.class);
        } catch (Exception e) {
            System.err.println("Failed to clone configuration safely: " + e.getMessage());
            return new AppConfiguration(); // Safe fallback
        }
    }
    public void saveCurrentConfig() {
        try {
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(activeConfig);
            SecureAppStorage.saveAppConfig(jsonString);
        } catch (Exception e) {
            System.err.println("Failed to write config updates: " + e.getMessage());
        }
    }

    /**
     * Updates the internal master reference with the modified data
     * and secure-flushes it directly down to AppData.
     *
     * @param updatedConfig The clone instance containing the new GUI choices
     */
    public void saveCurrentConfig(AppConfiguration updatedConfig) {
        try {
            // save the contents of updated config to AppData
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updatedConfig);
            SecureAppStorage.saveAppConfig(jsonString);

            // update active to a copy of the provided updated config
            this.activeConfig = objectMapper.readValue(jsonString, AppConfiguration.class);

            System.out.println("SpectralAnalyzer settings successfully saved to disk.");
        } catch (Exception e) {
            System.err.println("Failed to write config updates: " + e.getMessage());
        }
    }
}
