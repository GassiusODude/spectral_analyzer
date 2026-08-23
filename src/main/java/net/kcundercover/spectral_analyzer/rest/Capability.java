package net.kcundercover.spectral_analyzer.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Track capabilities by the base URL and the schema used.
 *
 * {@code baseUrl} is the base location for the REST API.
 * {@code method} is from {GET, POST, PUT, DELETE}.
 * {@code metadata} is the details of the operation.
 * {@code schema} is used to track things in the
 * 'components' (sometimes referenced in path)
 */
public class Capability {
    private static final Logger CAP_LOGGER = LoggerFactory.getLogger(Capability.class);

    // Move ObjectMapper to be a class field, initialized once
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String path;
    private final String baseUrl;
    private final JsonNode metadata;
    private final HttpMethod method;
    private final JsonNode schema;
    private final String apiKey;


    /**
     * Constructor
     * @param baseUrl The base URL for the capability
     * @param path The path from the base URL
     * @param method The HttpMethod (GET, POST)
     * @param metadata Json object from paths
     * @param root Root JSON object
     * @param apiKey API key to using service
     */
    private Capability(String baseUrl, String path, HttpMethod method, JsonNode metadata, JsonNode root, String apiKey) {
        this.baseUrl = baseUrl;
        this.path = path;
        this.method = method;
        this.metadata = metadata;
        this.apiKey = apiKey;
        this.schema = resolveProperties(metadata, root);
    }

    /**
     * Factory: Inspects a path node and creates Capabilities for all supported HTTP methods.
     * @param baseUrl The base URL to scan
     * @param path The paths from the base URL
     * @param pathNode The Json object representing the path
     * @param root The root node of hte OpenAPI json.path.
     * @param apiKey The API key to access the REST service
     * @return THe list of capabilities found.
     */
    public static List<Capability> fromPathNode(String baseUrl, String path, JsonNode pathNode, JsonNode root, String apiKey) {
        List<Capability> discovered = new ArrayList<>();
        String[] methodsList = {"get", "post", "put", "delete"};

        for (String currMethod : methodsList) {
            if (pathNode.has(currMethod)) {
                HttpMethod httpMethod = HttpMethod.fromString(currMethod);
                if (httpMethod != null) {
                    discovered.add(new Capability(baseUrl, path, httpMethod, pathNode.get(currMethod), root, apiKey));
                }
            }
        }
        return discovered;
    }

    /**
     * Resolve the properties from the Schema
     * @param operation The operation node for a specific path.
     * @param root The root node
     * @return The Json Node
     */
    private JsonNode resolveProperties(JsonNode operation, JsonNode root) {
        boolean isOpenApi = root.has("openapi");
        if (isOpenApi) {
            return resolveOpenApiProperties(operation, root);
        } else {
            return resolveSwaggerProperties(operation, root);
        }
    }

     // Helper method to resolve $ref for JSON schemas (like in FastAPI)
    private JsonNode resolveJsonSchema(JsonNode schemaNode, JsonNode root) {
        if (schemaNode.has("$ref")) {
            String refPath = schemaNode.get("$ref").asText().substring(1);
            return root.at(refPath + "/properties");
        }
        return schemaNode.path("properties");
    }
    private JsonNode resolveOpenApiProperties(JsonNode operation, JsonNode root) {
        ObjectNode flattenedParams = MAPPER.createObjectNode();

        // 1. Handle Request Body (JSON or Octet-Stream)
        // This is where most of your FastAPI payload definitions will be
        JsonNode requestBodyNode = operation.path("requestBody");
        if (!requestBodyNode.isMissingNode()) {
            // Check for application/json in requestBody
            JsonNode jsonSchemaNode = requestBodyNode.at("/content/application~1json/schema");
            if (!jsonSchemaNode.isMissingNode()) {
                JsonNode resolvedJsonProps = resolveJsonSchema(jsonSchemaNode, root);
                resolvedJsonProps.fields().forEachRemaining(entry ->
                    flattenedParams.set(entry.getKey(), entry.getValue()));
                CAP_LOGGER.info("Detected OpenAPI 3.0 JSON Request Body");
            }

            // Check for application/octet-stream in requestBody
            JsonNode binarySchemaNode = requestBodyNode.at("/content/application~1octet-stream/schema");
            if (!binarySchemaNode.isMissingNode()) {
                ObjectNode binaryParam = MAPPER.createObjectNode();
                binaryParam.put("description", "Byte Array");
                flattenedParams.set("Binary Request Body", binaryParam);
                CAP_LOGGER.info("Detected OpenAPI 3.0 Binary Request Body");
            }
        }

        // 2. Handle Parameters Array (query, header, path, cookie params)
        // This is where 'dtype' will be if it's a query parameter in OAI 3.0
        JsonNode parameters = operation.path("parameters");
        if (parameters.isArray()) {
            CAP_LOGGER.info("Processing OpenAPI 3.0 parameters field: {}", parameters.toPrettyString());
            for (JsonNode param : parameters) {
                String name = param.path("name").asText();
                JsonNode paramSchema = param.path("schema");

                if (paramSchema.isMissingNode()) { // Sometimes schema is direct (e.g., simple type)
                    ObjectNode directSchema = MAPPER.createObjectNode();
                    if (param.has("type")) {
                        directSchema.set("type", param.get("type"));
                    }
                    if (param.has("format")) {
                        directSchema.set("format", param.get("format"));
                    }
                    if (param.has("enum")) {
                        directSchema.set("enum", param.get("enum"));
                    }
                    if (param.has("description")) {
                        directSchema.set("description", param.get("description"));
                    }
                    paramSchema = directSchema;
                } else if (paramSchema.has("$ref")) { // Handle references for parameter schemas
                    String refPath = paramSchema.get("$ref").asText();
                    String jsonPointer = refPath.substring(1);
                    paramSchema = root.at(jsonPointer); // Resolve reference from root
                }
                flattenedParams.set(name, paramSchema);
                CAP_LOGGER.info("Added parameter '{}' with schema: {}", name, paramSchema.toString());
            }
        }

        return flattenedParams;
    }


// Inside Capability.java

// ... other methods ...

private JsonNode resolveSwaggerProperties(JsonNode operation, JsonNode root) {
    ObjectNode flattenedParams = MAPPER.createObjectNode();

    // 1. Check for consumes (Binary Request Body)
    boolean isBinary = false;
    if (operation.has("consumes")) {
        for (JsonNode c : operation.get("consumes")) {
            if ("application/octet-stream".equals(c.asText())) {
                isBinary = true;
                break;
            }
        }
    }

    if (isBinary) {
        // Add "Binary Request Body" if it's an octet-stream endpoint
        ObjectNode binaryParam = MAPPER.createObjectNode();
        binaryParam.put("type", "buffer");
        binaryParam.put("description", "Byte Array");
        flattenedParams.set("Binary Request Body", binaryParam);
        CAP_LOGGER.info("Detected Binary Request Body via consumes for Swagger 2.0");
    }

    // 2. Process the 'parameters' array (This is where 'dtype' will be found!)
    JsonNode parameters = operation.path("parameters");
    if (parameters.isArray()) {
        CAP_LOGGER.info("Processing Swagger 2.0 parameters field: {}", parameters.toPrettyString());
        for (JsonNode param : parameters) {
            String name = param.path("name").asText();
            JsonNode paramSchema = param.path("schema"); // This could be missing for Swagger 2.0 'type' field

            // Swagger 2.0 parameters don't always have a 'schema' object.
            // They can have 'type', 'format', 'enum' directly.
            if (paramSchema.isMissingNode()) {
                // If no 'schema' object, create one from direct parameter properties
                ObjectNode directSchema = MAPPER.createObjectNode();
                if (param.has("type")) {
                    directSchema.set("type", param.get("type"));
                }
                if (param.has("format")) {
                    directSchema.set("format", param.get("format"));
                }
                if (param.has("enum")) {
                    directSchema.set("enum", param.get("enum"));
                }
                if (param.has("description")) {
                    directSchema.set("description", param.get("description"));
                }
                paramSchema = directSchema; // Use this constructed schema
            } else {
                // Handle case where schema is defined in a reference (less common in direct params for Swagger 2.0)
                if (paramSchema.has("$ref")) {
                    String refPath = paramSchema.get("$ref").asText();
                    String jsonPointer = refPath.substring(1);
                    paramSchema = root.at(jsonPointer);
                }
            }
            flattenedParams.set(name, paramSchema);
            CAP_LOGGER.info("Added parameter '{}' with schema: {}", name, paramSchema.toString());
        }
    }

    return flattenedParams;
}
    /**
     * Get method for the baseUrl
     * @return The base URL for the capability
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get the path of the capability
     * @return Get the path of the capability, not including the base URL
     */
    public String getPath() {
        return path;
    }

    public String getApiKey() {
        return apiKey;
    }

    /**
     * Get method for the metadata
     * @return Return the metadata describing the capability (like parameters and output)
     */
    public JsonNode getMetadata() {
        return metadata;
    }
    /**
     * Get method for the 'method'.
     * @return Returns the type of method, {GET, POST, DELETE, PUT}
     */
    public HttpMethod getMethod() {
        return method;
    }

    /**
     * Get the schema (this is captured if the 'paths' section uses
     * references to custom objects in the 'components' section.  This
     * would be from the components section.)
     * @return The schema of interest
     */
    public JsonNode getSchema() {
        return schema;
    }

    /**
     * Print the state of capability
     */
    public void printState() {
        CAP_LOGGER.info("Base URL = {}\tMode= {}",
            this.baseUrl, this.method.toString());

        if (this.metadata != null) {
            CAP_LOGGER.info("metadata = {}",
                this.metadata.toPrettyString());
        }

        if (this.schema != null) {
            CAP_LOGGER.info("Schema = {}",
                this.schema.toPrettyString());
        }
    }
}
