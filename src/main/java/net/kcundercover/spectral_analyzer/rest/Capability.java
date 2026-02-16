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
    private final String path;
    private final String baseUrl;
    private final JsonNode metadata;
    private final HttpMethod method;
    private final JsonNode schema;


    /**
     * Constructor
     * @param baseUrl The base URL for the capability
     * @param path The path from the base URL
     * @param method The HttpMethod (GET, POST)
     * @param metadata Json object from paths
     * @param root Root JSON object
     */
    private Capability(String baseUrl, String path, HttpMethod method, JsonNode metadata, JsonNode root) {
        this.baseUrl = baseUrl;
        this.path = path;
        this.method = method;
        this.metadata = metadata;
        this.schema = resolveProperties(metadata, root);
    }

    /**
     * Factory: Inspects a path node and creates Capabilities for all supported HTTP methods.
     * @param baseUrl The base URL to scan
     * @param path The paths from the base URL
     * @param pathNode The Json object representing the path
     * @param root The root node of hte OpenAPI json.path.
     * @return THe list of capabilities found.
     */
    public static List<Capability> fromPathNode(String baseUrl, String path, JsonNode pathNode, JsonNode root) {
        List<Capability> discovered = new ArrayList<>();
        String[] methodsList = {"get", "post", "put", "delete"};

        for (String currMethod : methodsList) {
            if (pathNode.has(currMethod)) {
                HttpMethod httpMethod = HttpMethod.fromString(currMethod);
                if (httpMethod != null) {
                    discovered.add(new Capability(baseUrl, path, httpMethod, pathNode.get(currMethod), root));
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
        // NOTE: Get the RequestBody
        JsonNode schema = operation.at("/requestBody/content/application~1json/schema");

        if (!schema.isMissingNode()) {
            if (schema.has("$ref")) {
                // references to
                CAP_LOGGER.info("Reference link found");
                return root.at(schema.get("$ref").asText().substring(1) + "/properties");
            }
            CAP_LOGGER.info("schema is not missing node()");
            return schema.path("properties");
        }

        // Try to get parameters for GET/DELETE (Parameters Array)
        JsonNode parameters = operation.path("parameters");
        CAP_LOGGER.info("Parameters field " + parameters.toPrettyString());

        ObjectMapper mapper = new ObjectMapper();
        if (parameters.isArray()) {
            CAP_LOGGER.info("Capabilities has array of parameter");
            ObjectNode flattenedParams = mapper.createObjectNode();

            for (JsonNode param : parameters) {
                String name = param.path("name").asText();
                JsonNode paramSchema = param.path("schema");
                flattenedParams.set(name, paramSchema);
                CAP_LOGGER.info("Parmeter({}) with {}", name, paramSchema.toString());
            }
            return flattenedParams;
        }
        return mapper.createObjectNode(); // Return empty if nothing found
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
