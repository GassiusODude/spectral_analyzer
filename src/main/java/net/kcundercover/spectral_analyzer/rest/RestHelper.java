package net.kcundercover.spectral_analyzer.rest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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

    Map<String, Capability> capabilities = new HashMap<>();

    /**
     * Discover capabilities
     *
     * Access a REST API's schema
     * @param schemaUrl The path to JSON describing schema used
     */
    public void discover(String schemaUrl) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(schemaUrl)).build();
            String jsonString = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode root = mapper.readTree(jsonString);

            // Default to the schemaUrl's origin if servers are missing
            String baseUrl = "";
            if (root.has("servers") && root.get("servers").isArray() && root.get("servers").size() > 0) {
                // access 'servers' field if provided
                baseUrl = root.get("servers").get(0).get("url").asText();
            } else {
                // parse based on <HOST>:<PORT>
                URI uri = new URI(schemaUrl);
                baseUrl = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            }

            String finalBaseUrl = baseUrl;
            String[] supported = {"get", "post", "put", "delete"};

            root.path("paths").properties().forEach(entry -> {
                String path = entry.getKey();
                JsonNode pathNode = entry.getValue();

                for (String currMethod : supported) {
                    if (pathNode.has(currMethod)) {
                        HttpMethod httpMethod = HttpMethod.fromString(currMethod);
                        JsonNode operation = pathNode.get(currMethod);

                        if (httpMethod != null && operation != null) {
                            JsonNode requestBody = operation.path("requestBody").path("content").path("application/json").path("schema");
                            if (requestBody.has("$ref")) {

                                String refPath = requestBody.get("$ref").asText(); // e.g., "#/components/schemas/ClusterRequest"
                                String schemaName = refPath.substring(refPath.lastIndexOf('/') + 1);

                                // Look up the actual properties in the components section
                                JsonNode actualSchema = root.path("components").path("schemas").path(schemaName);
                                capabilities.put(path, new Capability(finalBaseUrl, httpMethod, operation, actualSchema));
                                RH_LOGGER.info("Capability discovered: [" + finalBaseUrl + "] " + path);
                            } else {
                                capabilities.put(path, new Capability(finalBaseUrl, httpMethod, operation, null));
                                RH_LOGGER.info("Capability discovered: [" + finalBaseUrl + "] " + path);
                            }
                        }
                        break; // Only take the first found method for this path
                    }
                }
            });

        } catch (Exception e) {
            showError("Discovery Error", e.getMessage());
        }
    }

    public void executeCapability(String path, Map<String, Object> userInputs) {
        Capability cap = capabilities.get(path);
        if (cap == null) {
            return;
        }

        String fullUri = cap.getBaseUrl() + path;

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

        try {
            // 2. Initialize Client and Build Request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUri + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            // 3. Execute Async
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(jsonString -> {
                    Platform.runLater(() -> {
                        try {
                            JsonNode jsonResponse = mapper.readTree(jsonString);
                            RH_LOGGER.info("Response = " + jsonResponse.toPrettyString());
                        } catch (Exception e) {
                            RH_LOGGER.info("Error parsing response: " + jsonString);
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> showError("Request Failed", ex.getMessage()));
                    return null;
                });

        } catch (Exception e) {
            showError("Setup Error", e.getMessage());
        }

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
