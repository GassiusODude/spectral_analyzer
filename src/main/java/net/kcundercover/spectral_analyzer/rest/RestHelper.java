package net.kcundercover.spectral_analyzer.rest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import javafx.scene.layout.GridPane;
// import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;
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
     * Determine the base URL from the schema URL
     *
     * The schema URL is different depending the the server type
     * With FastAPI, the schema is <HOST>:<PORT>/openapi.json
     * In spring boot it is in the form of <HOST>:<PORT>/v3/api-docs
     *
     * @param root Root of the JSON.
     * @param schemaUrl Schema URL
     * @return Return the base path.
     */
    public String determineBaseUrl(JsonNode root, String schemaUrl) {
        String baseUrl = "";
        if (root.has("servers") && root.get("servers").isArray() && root.get("servers").size() > 0) {
            // access 'servers' field if provided
            baseUrl = root.get("servers").get(0).get("url").asText();
        } else {
            // parse based on <HOST>:<PORT>
            try {
                URI uri = new URI(schemaUrl);
                baseUrl = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
            } catch(URISyntaxException uriSyntax) {
                RH_LOGGER.warn("URI Syntax error!!");
            }
        }
        return baseUrl;
    }

    /**
     * Query for the OpenAPI JSON for the paths, parameters/properties.
     * @param schemaUrl Path to the OpenAPI JSON schema
     */
    public void discover(String schemaUrl)  {
        ObjectMapper mapper = new ObjectMapper();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(schemaUrl)).build();
            String jsonString = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode root = mapper.readTree(jsonString);
            String baseUrl = determineBaseUrl(root, schemaUrl);

            root.path("paths").properties().forEach(entry -> {
                // Delegate all parsing logic to the Capability class
                List<Capability> pathCaps = Capability.fromPathNode(
                    baseUrl,
                    entry.getKey(),
                    entry.getValue(),
                    root
                );

                // Store them in the map
                for (Capability cap : pathCaps) {
                    // Using "METHOD /path" as key ensures uniqueness if a path has GET and POST
                    String uniqueKey = cap.getBaseUrl() + cap.getPath();
                    capabilities.put(uniqueKey, cap);
                    RH_LOGGER.info("Discovered: " + uniqueKey);
                }
            });
        } catch(Exception exc) {
            RH_LOGGER.warn("Caught {}", exc.toString());
        }
    }

    /**
     * Run the capabilty
     * @param cap Capability to run.
     * @param userInputs Map of inputs
     */
    public void executeCapability(Capability cap, Map<String, Object> userInputs) {
        if (cap == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();

        // Dynamically pack whatever the user typed into the JSON body
        userInputs.forEach((key, value) -> {
            RH_LOGGER.info("{} with value {} of type {}", key, value, value.getClass().getName());
            if (value instanceof Integer) {
                body.put(key, (Integer) value);
            } else if (value instanceof Double) {
                body.put(key, (Double) value);
            } else if (value instanceof double[][]) {
                // Handle the 2D array for things like "observations"
                body.putPOJO(key, value);
            }
        });


        // 2. Initialize Client and Build Request
        HttpClient client = HttpClient.newHttpClient();

        if (cap.getMethod() == HttpMethod.POST) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cap.getBaseUrl() + cap.getPath()))
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
        } else if (cap.getMethod() == HttpMethod.GET) {
            try {
                // ============================================================
                // Prepare Query for GET
                // ============================================================
                StringBuilder queryString = new StringBuilder("?");
                userInputs.forEach((key, value) -> {
                    if (queryString.length() > 1) {
                        queryString.append("&");
                    }
                    queryString.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
                });

                // ============================================================
                // Set up request
                // ============================================================
                client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cap.getBaseUrl() + cap.getPath() + queryString.toString()))
                    .GET() // Changed from .POST(...)
                    .build();

                // ============================================================
                // Send request and get response
                // ============================================================
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                RH_LOGGER.info("Response = {}", response);

                // Check if it was successful (Status code 200-299)
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    RH_LOGGER.info("✅ Success! Status Code: {}", response.statusCode());
                    RH_LOGGER.info("Response Body: {}", response.body());
                } else {
                    RH_LOGGER.error("❌ Request Failed! Status Code: {}", response.statusCode());
                    RH_LOGGER.error("Error Detail: {}", response.body());
                }

                String responseBody = response.body();

                // ============================================================
                // Prepare Information Dialog to show response
                // ============================================================
                TextArea textArea = new TextArea(responseBody);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setPrefHeight(300);
                textArea.setPrefWidth(500);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Identification Result");
                alert.setHeaderText("Server Response (Status: " + response.statusCode() + ")");
                alert.getDialogPane().setContent(textArea);
                alert.showAndWait();

            } catch (IOException ioe) {
                showError("IOException", ioe.getMessage());
            } catch (InterruptedException ie) {
                showError("Interupted Error", ie.getMessage());
            }
        }

    }

    public Set<String> getCapabilityPaths() {
        return capabilities.keySet();
    }
    public Capability getCapability(String path) {
        return capabilities.get(path);
    }

    /**
     * Design UI form based on schemaProperties
     * @param schemaProperties Schema properties
     * @param grid Gridpane for the dynamic GUI
     * @param inputFields GUI components to take in input
     */
    public void buildFormFromSchema(JsonNode schemaProperties, GridPane grid, Map<String, Control> inputFields) {
        int row = 0; // Move outside the forEach
        for (var entry : schemaProperties.properties()) {
            String propertyName = entry.getKey();
            JsonNode propertyDetails = entry.getValue();
            String type = propertyDetails.path("type").asText();

            grid.add(new Label(propertyName + ":"), 0, row);

            Control inputControl;
            if (propertyDetails.has("enum")) {
                ComboBox<String> combo = new ComboBox<>();
                propertyDetails.get("enum").forEach(val -> combo.getItems().add(val.asText()));
                inputControl = combo;
            } else if ("integer".equals(type)) {
                // Spinner for integers: Min, Max, Default
                inputControl = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0));
                ((Spinner<?>) inputControl).setEditable(true);
            } else if ("number".equals(type)) {
                // Spinner for doubles: Min, Max, Default, Amount to step by
                inputControl = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1));
                ((Spinner<?>) inputControl).setEditable(true);
            } else if ("boolean".equals(type)) {
                inputControl = new CheckBox();
            } else {
                inputControl = new TextField();
                ((TextField)inputControl).setPromptText(type);
            }

            grid.add(inputControl, 1, row);
            inputFields.put(propertyName, inputControl);
            row++;
        }
    }

    /**
     * Show dialog for an error
     * @param title Title on the dialog
     * @param content Content of the dialog
     */
    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Show Dialog box
     * @param owner
     * @param schemaProperties
     */
    public void showCapabilityDialog(Window owner, Capability cap) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Execute Capability");
        dialog.setHeaderText("Enter required parameters:");

        // ====================================================================
        // Setup the Grid and Buttons
        // ====================================================================
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        Map<String, Control> inputFields = new HashMap<>();
        buildFormFromSchema(cap.getSchema(), grid, inputFields);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                Map<String, Object> results = new HashMap<>();
                inputFields.forEach((name, control) -> {
                    if (control instanceof TextField) {
                        results.put(name, ((TextField) control).getText());
                    } else if (control instanceof ComboBox) {
                        results.put(name, ((ComboBox<?>) control).getValue());
                    } else if (control instanceof Spinner) {
                        // Extracts the numeric value (Integer or Double)
                        results.put(name, ((Spinner<?>) control).getValue());
                    } else if (control instanceof CheckBox) {
                        // Extracts the boolean true/false
                        results.put(name, ((CheckBox) control).isSelected());
                    }
                });
                return results;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(inputs -> {
            executeCapability(cap, inputs);
        });
    }

}
