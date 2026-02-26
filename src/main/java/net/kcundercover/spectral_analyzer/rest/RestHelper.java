package net.kcundercover.spectral_analyzer.rest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
// import java.security.DrbgParameters.Capability;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
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

import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.spectral_analyzer.data.IqData;

/**
 * Helper to set up dynamic capabilities through REST API
 */
public class RestHelper {
    private static final Logger RH_LOGGER = LoggerFactory.getLogger(RestHelper.class);

    /** Store of capabilities mapped by URL path */
    Map<String, Capability> capabilities = new HashMap<>();

    /** Default constructor */
    public RestHelper() {

    }

    /**
     * Determine the base URL from the schema URL
     *
     * The schema URL is different depending the the server type
     * With FastAPI, the schema is HOST:PORT/openapi.json
     * In spring boot it is in the form of HOST:PORT/v3/api-docs
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
    public void discover(String schemaUrl, String apiKey) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            HttpClient client = HttpClient.newHttpClient();

            // NOTE: support x-api-key
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(schemaUrl))
                .header("x-api-key", apiKey) // Pass the secret to FastAPI
                .header("Accept", "application/json")
                .GET()
                .build();

            // send request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check for 403 Forbidden or 401 Unauthorized
            if (response.statusCode() == 403 || response.statusCode() == 401) {
                RH_LOGGER.error("Access Denied: Invalid API Key for {}", schemaUrl);
                return;
            }

            String jsonString = response.body();
            JsonNode root = mapper.readTree(jsonString);
            String baseUrl = determineBaseUrl(root, schemaUrl);

            root.path("paths").properties().forEach(entry -> {
                // Delegate all parsing logic to the Capability class
                List<Capability> pathCaps = Capability.fromPathNode(
                    baseUrl,
                    entry.getKey(),
                    entry.getValue(),
                    root,
                    apiKey
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

        // Initialize Client and Build Request
        HttpClient client = HttpClient.newHttpClient();

        if (cap.getMethod() == HttpMethod.POST) {
            // ============================================================
            // Prepare POST request
            // ============================================================
            userInputs.forEach((key, value) -> {
                String schemaType = cap.getSchema().path(key).path("type").asText();
                RH_LOGGER.debug("{} with value of type {}, Schema = {}",
                    key, value.getClass().getName(), schemaType);

                if (value instanceof Integer) {
                    body.put(key, (Integer) value);
                } else if (value instanceof Double) {
                    body.put(key, (Double) value);
                } else if (value instanceof String) {
                    if ("array".equals(schemaType)) {
                        RH_LOGGER.info("Treating {} as JSON Tree", key);
                        try {
                            JsonNode tmpArray = mapper.readTree(value.toString());
                            // body.set(key, tmpArray);
                            body.putPOJO(key, tmpArray);
                            RH_LOGGER.info("Body = {}", tmpArray.toPrettyString());
                        } catch(JsonProcessingException jpe) {
                            RH_LOGGER.warn("Json process error " + jpe.toString());
                        }
                    } else {
                        body.put(key, (String) value);
                        RH_LOGGER.debug("Treating {} as String for shema {}", key, schemaType);
                    }
                } else if (value instanceof Boolean b) {

                    body.put(key, b);
                } else {
                    RH_LOGGER.info("Got {} = {}, key={} of class ={}", key, value, key, value.getClass().toString());
                }
            });

            // ============================================================
            // Send POST request
            // ============================================================
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cap.getBaseUrl() + cap.getPath()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        String jsonString = response.body();
                        int statusCode = response.statusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            try {
                                JsonNode jsonResponse = mapper.readTree(jsonString);
                                RH_LOGGER.info("Success: Response = " + jsonResponse.toPrettyString());
                            } catch (Exception e) {
                                RH_LOGGER.info("Success Status, but error parsing response: " + jsonString);
                                showError("Success Status error parsing", "Success Status received, failed to parse response");
                            }
                        } else {
                            RH_LOGGER.error("Server Error " + statusCode + ": " + jsonString);
                            showError("Server Error", "Server return an error (" + statusCode + ")");
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
                    .header("x-api-key", cap.getApiKey()) // Pass the secret to FastAPI
                    .header("Accept", "application/json")
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

    /**
     * Get the capability paths
     * @return Return a set of paths
     */
    public Set<String> getCapabilityPaths() {
        return capabilities.keySet();
    }

    /**
     * Get the capability based on name
     * @param path The path of interest
     * @return The capability object
     */
    public Capability getCapability(String path) {
        return capabilities.get(path);
    }

    /**
     * Check if the object type matches the schema type
     * @param value The object value
     * @param schemaType The data type described in the schema
     * @return Return true if compatible type to schema
     */
    private boolean isTypeCompatible(Object value, String schemaType) {
        if (value == null) {
            return false;
        }
        return switch (schemaType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number; // Covers Double, Float, Integer, etc.
            case "boolean" -> value instanceof Boolean;
            case "array" -> value.getClass().isArray() || value instanceof java.util.Collection;
            default -> false;
        };
    }

    /**
     * Update the control with the specified value
     * @param control The GUI control component
     * @param value The data object associated.
     */
    private void updateControlValue(Control control, Object value) {
        if (value == null) {
            return;
        }
        String strValue;
        if (value.getClass().isArray()) {
            strValue = java.util.Arrays.deepToString((Object[]) value);
        } else {
            strValue = value.toString();
        }
        if (control instanceof TextField tf) {
            tf.setText(strValue);
        } else if (control instanceof Spinner spinner) {
            // We use a helper to set the value safely
            spinner.getValueFactory().setValue(value);
        } else if (control instanceof CheckBox cb && value instanceof Boolean b) {
            cb.setSelected(b);
        } else if (control instanceof ComboBox combo) {
            // If the main input is also a combo (for enums)
            combo.setValue(value.toString());
        }
    }


    /**
     * Design UI form based on schemaProperties
     * @param schemaProperties Schema properties
     * @param grid Gridpane for the dynamic GUI
     * @param inputFields GUI components to take in input
     * @param iq IQ data and SigMF properties
     */
    public void buildFormFromSchema(JsonNode schemaProperties, GridPane grid, Map<String, Control> inputFields, IqData iq) {
        int row = 0;

        Map<String, Object> dataMap = iq.getData();

        for (var entry : schemaProperties.properties()) {
            String propertyName = entry.getKey();
            JsonNode propertyDetails = entry.getValue();
            String type = propertyDetails.path("type").asText();

            grid.add(new Label(propertyName + ":"), 0, row);

            // initialize a control UI and a combo box with similiar data types
            Control inputControl;
            ComboBox<String> comboData = new ComboBox<>();

            // ----------------------------------------------------------------
            // Prepare the input control UI in current row
            // ----------------------------------------------------------------
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

            // ----------------------------------------------------------------
            // Prepare combo box for simple input in this row
            // ----------------------------------------------------------------
            // update the keys to properties with matching data types
            for (Map.Entry<String, Object> dataEntry: dataMap.entrySet()) {
                String key = dataEntry.getKey();
                Object value = dataEntry.getValue();
                if (isTypeCompatible(value, type)) {
                    comboData.getItems().add(key);
                }
            }

            // add selection listener to update control if selection is made
            comboData.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    Object sourceValue = dataMap.get(newVal);
                    updateControlValue(inputControl, sourceValue);
                }
            });


            // ----------------------------------------------------------------
            // update grid
            // ----------------------------------------------------------------
            grid.add(inputControl, 1, row);
            grid.add(comboData, 2, row);
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
     * @param owner Owner window to align the dialog
     * @param cap The capability
     * @param iq The iq data being applied towards
     */
    public void showCapabilityDialog(Window owner, Capability cap, IqData iq) {
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
        buildFormFromSchema(cap.getSchema(), grid, inputFields, iq);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                Map<String, Object> results = new HashMap<>();
                inputFields.forEach((name, control) -> {
                    String schemaType = cap.getSchema().path("properties").path(name).path("type").asText();
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
