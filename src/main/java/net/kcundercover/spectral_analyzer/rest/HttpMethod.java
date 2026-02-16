
package net.kcundercover.spectral_analyzer.rest;

/**
 * Enumeration of HTTP Methods
 */
public enum HttpMethod {
    /** HTTP GET request for retrieving resources. */
    GET,

    /** HTTP POST request for creating resources. */
    POST,

    /** HTTP PUT request for updating resources. */
    PUT,

    /** HTTP DELETE request for removing resources. */
    DELETE;

    /**
     * Safely resolve a string to an HttpMethod
     * @param method The name of the method to create
     * @return The HttpMethod requested.
     */
    public static HttpMethod fromString(String method) {
        if (method == null) {
            return null;
        }

        for (HttpMethod currMethod : HttpMethod.values()) {
            if (currMethod.name().equalsIgnoreCase(method)) {
                return currMethod;
            }
        }
        return null;
    }
}
