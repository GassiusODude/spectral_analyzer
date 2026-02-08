package net.kcundercover.spectral_analyzer.rest;

import com.fasterxml.jackson.databind.JsonNode;

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
    private final String baseUrl;
    private final JsonNode metadata;
    private final HttpMethod method;
    private final JsonNode schema;

    public Capability(String baseUrl, HttpMethod method, JsonNode metadata, JsonNode schema) {
        this.baseUrl = baseUrl;
        this.metadata = metadata;
        this.method = method;
        this.schema = schema;
    }

    /**
     * Get method for the baseUrl
     * @return The base URL for the capability
     */
    public String getBaseUrl() {
        return baseUrl;
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
     * @return
     */
    public JsonNode getSchema() {
        return schema;
    }
}
