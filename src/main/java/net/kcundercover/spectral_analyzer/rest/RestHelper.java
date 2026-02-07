package net.kcundercover.spectral_analyzer.rest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.io.IOException;
import java.net.URI;
import javafx.scene.layout.GridPane;
// import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;


/**
 * Helper to set up dynamic capabilities through REST API
 */
public class RestHelper {
    private static final Logger RH_LOGGER = LoggerFactory.getLogger(RestHelper.class);

    Map<String, JsonNode> capabilities = new HashMap<>();

    /**
     * Discover capabilities
     * @param schemaUrl The path to JSON describing schema used
     */
    public void discover(String schemaUrl) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try {
            // --------------------  connect and request schema  ------------------
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(schemaUrl))
                .build();

            String jsonString = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

            // Parse the string
            JsonNode root = mapper.readTree(jsonString);

            // Find all POST endpoints (our "capabilities")
            root.path("paths").properties().forEach(entry -> {
                String path = entry.getKey();
                if (entry.getValue().has("get")) {
                    capabilities.put(path, entry.getValue().get("get"));
                    RH_LOGGER.info("New capability found: " + path);
                } else if (entry.getValue().has("post")) {
                    capabilities.put(path, entry.getValue().get("post"));
                    RH_LOGGER.info("New capability found: " + path);
                }
            });

        } catch (IllegalArgumentException e) {
            // Triggered if the string isn't a valid URI (e.g., "not a link")
            showError("Invalid URL format", "Please check the address and try again.");
        } catch (IOException e) {
            // Triggered if server is down, 404, or network is disconnected
            showError("Connection Failed", "Could not reach the server. Is it running?");
        } catch (Exception e) {
            // Catch-all for unexpected issues
            showError("Unexpected Error", e.getMessage());
        }

    }

    public void executeCapability(String path, Map<String, Object> userInputs) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();

        // Dynamically pack whatever the user typed into the JSON body
        userInputs.forEach((key, value) -> {
            if (value instanceof Integer) {
                body.put(key, (Integer) value);
            } else if (value instanceof Double) {
                body.put(key, (Double) value);
            } else if (value instanceof double[][]) {
                // Handle the 2D array for things like "observations"
                body.putPOJO(key, value);
            }
        });

        // Send via standard POST...
        // client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        //     .thenApply(response -> {
        //         try {
        //             return mapper.readTree(response.body());
        //         } catch (Exception e) { return null; }
        //     })
        //     .thenAccept(jsonResponse -> {
        //         // Update JavaFX UI Label/Table with whatever keys came back
        //         Platform.runLater(() -> {
        //             uiTextArea.setText(jsonResponse.toPrettyString());
        //         });
        //     });

    }



    /**
     * Design UI form based on schemaProperties
     * @param schemaProperties
     * @param grid
     */
    public void buildFormFromSchema(JsonNode schemaProperties, GridPane grid) {

        // Map to keep track of created fields so we can read them later
        Map<String, Control> inputFields = new HashMap<>();

        schemaProperties.properties().forEach(entry -> {
            int row = 0;
            String propertyName = entry.getKey();
            JsonNode propertyDetails = entry.getValue();
            String type = propertyDetails.path("type").asText();

            // prepare label and input control per field
            grid.add(new Label(propertyName + ":"), 0, row);
            Control inputControl;
            if ("integer".equals(type) || "number".equals(type)) {
                inputControl = new TextField(); // You can add numeric-only logic here
            } else if (propertyDetails.has("enum")) {
                inputControl = new ComboBox<String>(); // Populate with enum values
            } else {
                inputControl = new TextField();
            }

            grid.add(inputControl, 1, row);
            inputFields.put(propertyName, inputControl);
            row++;
        });
    }


    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

}
