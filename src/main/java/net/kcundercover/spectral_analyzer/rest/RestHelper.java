package net.kcundercover.spectral_analyzer.rest;

import java.io.IOException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.StreamSupport;

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
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.util.converter.DoubleStringConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.kcundercover.spectral_analyzer.data.IqData;
import net.kcundercover.spectral_analyzer.rest.Capability;

/**
 * Helper to set up dynamic capabilities through REST API
 */
public class RestHelper {
    private static final Logger RH_LOGGER = LoggerFactory.getLogger(RestHelper.class);
    public static final long MAX_SIZE = 50 * 1024 * 1024; // 50 MB limit
    /** Store of capabilities mapped by URL path */
    Map<String, Capability> capabilities = new HashMap<>();

    /**
     * Save JWT token securely to user preferences (Java Preferences API / AppData node)
     * @param serverKey unique identifier for the server (e.g. baseUrl)
     * @param token JWT token string
     */
    public static void saveAuthToken(String serverKey, String token) {
        Preferences prefs = Preferences.userNodeForPackage(RestHelper.class);
        if (token != null && !token.trim().isEmpty()) {
            prefs.put("JWT_TOKEN_" + serverKey.hashCode(), token);
            RH_LOGGER.info("Saved JWT token securely for server: {}", serverKey);
        } else {
            prefs.remove("JWT_TOKEN_" + serverKey.hashCode());
            RH_LOGGER.info("Removed JWT token for server: {}", serverKey);
        }
    }

    /**
     * Retrieve stored JWT token for the specified server from user preferences.
     * @param serverKey unique identifier for the server (e.g. baseUrl)
     * @return JWT token or null if not found
     */
    public static String getAuthToken(String serverKey) {
        Preferences prefs = Preferences.userNodeForPackage(RestHelper.class);
        return prefs.get("JWT_TOKEN_" + serverKey.hashCode(), null);
    }
    private HttpClient createHttpClient(boolean allowInsecure) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL);

        if (allowInsecure) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                };

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                RH_LOGGER.warn("Failed to initialize insecure SSL context, falling back to default: {}", e.getMessage());
            }
        }

        return builder.build();
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
            boolean isLocal = schemaUrl.contains("localhost") || schemaUrl.contains("127.0.0.1");
            HttpClient client = createHttpClient(isLocal);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(schemaUrl))
                .header("Accept", "application/json");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("x-api-key", apiKey);
            }
            HttpRequest request = requestBuilder.GET().build();

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

    public String executeCapability(Capability cap, Map<String, Object> userInputs, IqData iq){
        if (cap == null) {
            return "cap is null...abort!";
        }

        ObjectMapper mapper = new ObjectMapper();

        boolean isLocal = cap.getBaseUrl().contains("localhost") || cap.getBaseUrl().contains("127.0.0.1");
        HttpClient client = createHttpClient(isLocal);

        if (cap.getMethod() == HttpMethod.POST) {
            // ============================================================
            // Prepare POST request (Query Parameters vs JSON Request Body vs Binary Body)
            // ============================================================
            final byte[][] binaryPayloadWrapper = new byte[1][];
            Map<String, Object> jsonPayloadMap = new HashMap<>();
            StringBuilder queryParams = new StringBuilder("?");

            userInputs.forEach((key, value) -> {
                if ("Binary Request Body".equals(key)) {
                    String selectedBufferKey = value.toString();
                    binaryPayloadWrapper[0] = (byte[]) iq.getDataBuffer().get(selectedBufferKey);
                    RH_LOGGER.info("key = " + key + "\tSelected = " + selectedBufferKey);
                } else {
                    // Check if the schema defines this parameter as in "query" or if it belongs in the request body
                    // (Authentication/register endpoints put properties in JSON body; parameter-heavy routes use query string)
                    boolean isQueryParam = cap.getMetadata().has("parameters") &&
                        StreamSupport.stream(cap.getMetadata().get("parameters").spliterator(), false)
                            .anyMatch(p -> key.equals(p.path("name").asText()) && "query".equals(p.path("in").asText()));

                    if (isQueryParam) {
                        if (queryParams.length() > 1) {
                            queryParams.append("&");
                        }
                        String encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
                        queryParams.append(key).append("=").append(encodedValue);
                    } else {
                        // Otherwise, treat as JSON request body (e.g. login/register request body fields)
                        jsonPayloadMap.put(key, value);
                    }
                }
            });

            byte[] binaryPayload = binaryPayloadWrapper[0];
            String fullUrl = cap.getBaseUrl() + cap.getPath() + (queryParams.length() > 1 ? queryParams.toString() : "");

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl));

            if (cap.getApiKey() != null && !cap.getApiKey().trim().isEmpty()) {
                requestBuilder.header("x-api-key", cap.getApiKey());
            }

            // Automatically attach Bearer token if available for this server
            String jwtToken = getAuthToken(cap.getBaseUrl());
            if (jwtToken != null && !jwtToken.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + jwtToken);
            }

            if (binaryPayload != null) {
                if (binaryPayload.length > MAX_SIZE) {
                    RH_LOGGER.error("Payload too large: " + binaryPayload.length + " bytes. Capping at 50MB.");
                    return "REST capability exceeds maximum binary Payload of 50 MB";
                } else {
                    RH_LOGGER.trace("Binary Payload added to request with " + binaryPayload.length + " bytes");
                    requestBuilder.header("Content-Type", "application/octet-stream")
                                  .POST(HttpRequest.BodyPublishers.ofByteArray(binaryPayload));
                }
            } else if (!jsonPayloadMap.isEmpty()) {
                boolean isFormUrlEncoded = cap.getMetadata().path("requestBody")
                    .path("content").has("application/x-www-form-urlencoded");

                try {
                    if (isFormUrlEncoded) {
                        StringBuilder formBody = new StringBuilder();
                        jsonPayloadMap.forEach((k, v) -> {
                            if (formBody.length() > 0) {
                                formBody.append("&");
                            }
                            formBody.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                                    .append("=")
                                    .append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
                        });
                        RH_LOGGER.trace("Form-urlencoded Request Body added to request: {}", formBody);
                        requestBuilder.header("Content-Type", "application/x-www-form-urlencoded")
                                      .header("Accept", "application/json")
                                      .POST(HttpRequest.BodyPublishers.ofString(formBody.toString(), StandardCharsets.UTF_8));
                    } else {
                        String jsonBody = mapper.writeValueAsString(jsonPayloadMap);
                        RH_LOGGER.trace("JSON Request Body added to request: {}", jsonBody);
                        requestBuilder.header("Content-Type", "application/json")
                                      .header("Accept", "application/json")
                                      .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    RH_LOGGER.error("Failed to serialize payload: {}", e.getMessage());
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                }
            } else {
                RH_LOGGER.trace("POST request with no body");
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String jsonString = response.body();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode jsonResponse = mapper.readTree(jsonString);
                    // Check if response contains access_token and automatically save it
                    if (jsonResponse.has("access_token")) {
                        String token = jsonResponse.get("access_token").asText();
                        saveAuthToken(cap.getBaseUrl(), token);
                        RH_LOGGER.info("Saved JWT token securely for server for {} request: {}", cap.getMethod(), cap.getBaseUrl());
                    }
                    return jsonResponse.toPrettyString();
                } else {
                    return "Error: " + response.statusCode();
                }
            } catch (Exception e) {
                return "Failed: " + e.getMessage();
            }
                // client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                // .thenApply(response -> {
                    // String jsonString = response.body();
                    // int statusCode = response.statusCode();
                    // if (statusCode >= 200 && statusCode < 300) {

                    //     try {
                    //         JsonNode jsonResponse = mapper.readTree(jsonString);
                    //         RH_LOGGER.info("Success: Response = " + jsonResponse.toPrettyString());
                    //         return jsonResponse.toPrettyString();

                    //     } catch (JsonProcessingException jpe) {
                    //         return "Successful POST but error processing JSON response " + jpe.toString();
                    //     }
                    // } else {
                    //     return "Received status code = (" + statusCode + ") with " + response.body();
                    // }

                // })
                // .exceptionally(ex -> {
                //     return "Request Failed with " + ex.getMessage();
                // });

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
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(cap.getBaseUrl() + cap.getPath() + queryString.toString()))
                    .header("Accept", "application/json");
                if (cap.getApiKey() != null && !cap.getApiKey().trim().isEmpty()) {
                    reqBuilder.header("x-api-key", cap.getApiKey());
                }
                HttpRequest request = reqBuilder.GET().build();

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
                return responseBody;

            } catch (IOException ioe) {
                return "IOException = " + ioe.getMessage();
            } catch (InterruptedException ie) {
                return "InterruptedException = " + ie.getMessage();
            }
        }
        return "Something is wrong...should have a different return before this";
    }



    /**
     * Run the capabilty
     * @param cap Capability to run.
     * @param userInputs Map of inputs
     */
    public void executeCapability(Window owner, Capability cap, Map<String, Object> userInputs, IqData iq) {
        if (cap == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();

        boolean isLocal = cap.getBaseUrl().contains("localhost") || cap.getBaseUrl().contains("127.0.0.1");
        HttpClient client = createHttpClient(isLocal);
        if (cap.getMethod() == HttpMethod.POST) {
            // ============================================================
            // Prepare POST request (Query Parameters vs JSON Request Body vs Binary Body)
            // ============================================================
            final byte[][] binaryPayloadWrapper = new byte[1][];
            Map<String, Object> jsonPayloadMap = new HashMap<>();
            StringBuilder queryParams = new StringBuilder("?");

            userInputs.forEach((key, value) -> {
                if ("Binary Request Body".equals(key)) {
                    String selectedBufferKey = value.toString();
                    binaryPayloadWrapper[0] = (byte[]) iq.getDataBuffer().get(selectedBufferKey);
                    RH_LOGGER.info("key = " + key + "\tSelected = " + selectedBufferKey);
                } else {
                    boolean isQueryParam = cap.getMetadata().has("parameters") &&
                        StreamSupport.stream(cap.getMetadata().get("parameters").spliterator(), false)
                            .anyMatch(p -> key.equals(p.path("name").asText()) && "query".equals(p.path("in").asText()));

                    if (isQueryParam) {
                        if (queryParams.length() > 1) {
                            queryParams.append("&");
                        }
                        String encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
                        queryParams.append(key).append("=").append(encodedValue);
                    } else {
                        jsonPayloadMap.put(key, value);
                    }
                }
            });

            byte[] binaryPayload = binaryPayloadWrapper[0];
            String fullUrl = cap.getBaseUrl() + cap.getPath() + (queryParams.length() > 1 ? queryParams.toString() : "");

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl));

            if (cap.getApiKey() != null && !cap.getApiKey().trim().isEmpty()) {
                requestBuilder.header("x-api-key", cap.getApiKey());
            }

            // Automatically attach Bearer token if available for this server
            String jwtToken = getAuthToken(cap.getBaseUrl());
            if (jwtToken != null && !jwtToken.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + jwtToken);
            }

            if (binaryPayload != null) {
                if (binaryPayload.length > MAX_SIZE) {
                    RH_LOGGER.error("Payload too large: " + binaryPayload.length + " bytes. Capping at 50MB.");
                    showError(
                        owner,
                        "REST Capability Size Limit",
                        "Data max limit of 50 MB exceeded!!  Not sending request");
                    return;
                } else {
                    RH_LOGGER.trace("Binary Payload added to request with " + binaryPayload.length + " bytes");
                    requestBuilder.header("Content-Type", "application/octet-stream")
                                  .POST(HttpRequest.BodyPublishers.ofByteArray(binaryPayload));
                }
            } else if (!jsonPayloadMap.isEmpty()) {
                boolean isFormUrlEncoded = cap.getMetadata().path("requestBody")
                    .path("content").has("application/x-www-form-urlencoded");

                try {
                    if (isFormUrlEncoded) {
                        StringBuilder formBody = new StringBuilder();
                        jsonPayloadMap.forEach((k, v) -> {
                            if (formBody.length() > 0) {
                                formBody.append("&");
                            }
                            formBody.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                                    .append("=")
                                    .append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
                        });
                        RH_LOGGER.trace("Form-urlencoded Request Body added to request: {}", formBody);
                        requestBuilder.header("Content-Type", "application/x-www-form-urlencoded")
                                      .header("Accept", "application/json")
                                      .POST(HttpRequest.BodyPublishers.ofString(formBody.toString(), StandardCharsets.UTF_8));
                    } else {
                        String jsonBody = mapper.writeValueAsString(jsonPayloadMap);
                        RH_LOGGER.trace("JSON Request Body added to request: {}", jsonBody);
                        requestBuilder.header("Content-Type", "application/json")
                                      .header("Accept", "application/json")
                                      .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    RH_LOGGER.error("Failed to serialize payload: {}", e.getMessage());
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                }
            } else {
                RH_LOGGER.trace("POST request with no body");
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest request = requestBuilder.build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        String jsonString = response.body();
                        int statusCode = response.statusCode();
                        if (statusCode >= 200 && statusCode < 300) {

                             try {
                                 JsonNode jsonResponse = mapper.readTree(jsonString);
                                 if (jsonResponse.has("access_token")) {
                                     String token = jsonResponse.get("access_token").asText();
                                     saveAuthToken(cap.getBaseUrl(), token);
                                     RH_LOGGER.info("Saved JWT token securely for server: {}", cap.getBaseUrl());
                                 }
                                 RH_LOGGER.info("Success: Response = " + jsonResponse.toPrettyString());

                                // ============================================================
                                // Prepare Information Dialog to show response
                                // ============================================================
                                TextArea textArea = new TextArea(jsonResponse.toPrettyString());
                                textArea.setEditable(false);
                                textArea.setWrapText(true);
                                textArea.setPrefHeight(300);
                                textArea.setPrefWidth(500);
                                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                alert.initOwner(owner);
                                alert.setTitle("REST POST Results");
                                alert.setHeaderText("Server Response (Status: " + response.statusCode() + ")");
                                alert.getDialogPane().setContent(textArea);
                                alert.showAndWait();
                            } catch (JsonProcessingException jpe) {
                                showError(owner, "Error processing JSON response", jpe.toString());
                            }
                        } else {

                            String codeMessage =  "Server Error (" + statusCode + ")";
                            switch (statusCode) {
                                case 400 -> codeMessage = "Bad Request (400)";
                                case 401 -> codeMessage = "Unauthorized (401)";
                                case 402 -> codeMessage = "Payment Required (402)";
                                case 403 -> codeMessage = "Forbidden (403)";
                                case 404 -> codeMessage = "Not Found (404)";
                                case 422 -> codeMessage = "Unprocessable Entity (422)";
                                case 429 -> codeMessage = "Too Many Requests (429)";
                                case 500 -> codeMessage = "Internal Server Error (500)";
                                default -> {
                                }
                            }

                            RH_LOGGER.error(codeMessage + ": " + response.body());
                            showError(owner, codeMessage, response.body());
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> showError(owner, "Request Failed", ex.getMessage()));
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
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(cap.getBaseUrl() + cap.getPath() + queryString.toString()))
                    .header("Accept", "application/json");
                if (cap.getApiKey() != null && !cap.getApiKey().trim().isEmpty()) {
                    reqBuilder.header("x-api-key", cap.getApiKey());
                }
                HttpRequest request = reqBuilder.GET().build();

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
                alert.initOwner(owner);
                alert.setTitle("Identification Result");
                alert.setHeaderText("Server Response (Status: " + response.statusCode() + ")");
                alert.getDialogPane().setContent(textArea);
                alert.showAndWait();

            } catch (IOException ioe) {
                showError(owner, "IOException", ioe.getMessage());
            } catch (InterruptedException ie) {
                showError(owner, "Interupted Error", ie.getMessage());
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
    public void buildFormFromSchema(JsonNode schemaProperties, GridPane grid, Map<String, CapabilityConfig2> inputFields, IqData iq) {
        int row = 0;

        Map<String, Object> dataMap = iq.getData();
        Map<String, Object> dataMapBuffer = iq.getDataBuffer();
        for (var entry : schemaProperties.properties()) {
            String propertyName = entry.getKey();
            JsonNode propertyDetails = entry.getValue();
            String type = propertyDetails.path("type").asText();

            grid.add(new Label(propertyName + ":"), 0, row);

            // initialize a control UI and a combo box with similiar data types
            Control inputControl;
            ComboBox<String> comboData = new ComboBox<>();
            TextField selectTF = new TextField();

            // ----------------------------------------------------------------
            // Prepare the input control UI in current row
            // ----------------------------------------------------------------
            if ("buffer".equals(type)) {
                // NOTE: buffer type, get from IQ data
                ComboBox<String> combo = new ComboBox<>();
                for (Map.Entry<String, Object> dataEntry : dataMapBuffer.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();

                    // Only add if the value is actually a binary format
                    if (value instanceof byte[] || value instanceof java.nio.ByteBuffer) {
                        combo.getItems().add(key);
                    }
                }
                inputControl = combo;

            } else if (propertyDetails.has("enum")) {
                RH_LOGGER.trace("Enum for " + propertyName + ":\n" + propertyDetails.toPrettyString());
                ComboBox<String> combo = new ComboBox<>();
                propertyDetails.get("enum").forEach(val -> combo.getItems().add(val.asText()));
                inputControl = combo;
            } else if ("integer".equals(type)) {
                // Spinner for integers: Min, Max, Default
                int min = propertyDetails.path("minimum").asInt(0);
                int max = propertyDetails.path("maximum").asInt(Integer.MAX_VALUE);
                int defaultValue = propertyDetails.path("default").asInt(0);
                inputControl = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                    min, max, defaultValue));
                ((Spinner<?>) inputControl).setEditable(true);
            } else if ("number".equals(type)) {
                TextField inputField = new TextField();

                double defaultValue = propertyDetails.path("default").asDouble(0.0);
                inputField.setText(String.valueOf(defaultValue));

                TextFormatter<Double> formatter = new TextFormatter<>(new DoubleStringConverter(), defaultValue);
                inputField.setTextFormatter(formatter);

                // Access the value safely later
                Double finalValue = formatter.getValue();
                inputControl = inputField;

            } else if ("boolean".equals(type)) {
                inputControl = new CheckBox();
            } else {
                inputControl = new TextField();
                ((TextField)inputControl).setPromptText(type);
            }
            RH_LOGGER.trace("Property = " + propertyName + "\t(Type = " + type + ")");

            // ----------------------------------------------------------------
            // Prepare combo box for simple input in this row
            // ----------------------------------------------------------------
            if ("buffer".equals(type)) {
                for (Map.Entry<String, Object> dataEntry: dataMapBuffer.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    comboData.getItems().add(key);
                }
            } else {
                // update the keys to properties with matching data types
                for (Map.Entry<String, Object> dataEntry: dataMap.entrySet()) {
                    String key = dataEntry.getKey();
                    Object value = dataEntry.getValue();
                    if (isTypeCompatible(value, type)) {
                        comboData.getItems().add(key);
                    }
                }
            }

            // add selection listener to update control if selection is made
            comboData.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    Object sourceValue = dataMap.get(newVal);
                    updateControlValue(inputControl, sourceValue);
                    updateControlValue(selectTF, newVal);
                    // selectedText = newVal;
                }
            });

            // ----------------------------------------------------------------
            // update grid
            // ----------------------------------------------------------------
            grid.add(inputControl, 1, row);

            // NOTE: check "buffer" or "enum",
            //       both use ComboBox for inputField  already
            if (!("buffer".equals(type) || propertyDetails.has("enum")) ) {
                grid.add(comboData, 2, row);
            }

            inputFields.put(propertyName, new CapabilityConfig2(inputControl, selectTF));
            row++;
        }
    }

    /**
     * Show dialog for an error
     * @param owner Window being called from to center the new dialog
     * @param title Title on the dialog
     * @param content Content of the dialog
     */
    private void showError(Window owner, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(owner);
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

        Map<String, CapabilityConfig2> inputFields = new HashMap<>();
        buildFormFromSchema(cap.getSchema(), grid, inputFields, iq);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                Map<String, Object> results = new HashMap<>();
                inputFields.forEach((name, capConfig) -> {
                    Control control = capConfig.control;
                    String select = capConfig.iqDataField.getText();
                    RH_LOGGER.info("For " + name + " selection = " + select);
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
            executeCapability(owner, cap, inputs, iq);
        });
    }
}

class CapabilityConfig2 {
    public CapabilityConfig2(Control control, TextField iqDataField) {
        this.control = control;
        this.iqDataField = iqDataField;
    }
    public Control control;
    public TextField iqDataField;
}
