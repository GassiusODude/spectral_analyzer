
package net.kcundercover.spectral_analyzer.rest;

public enum HttpMethod {
    GET, POST, PUT, DELETE;

    /**
     * Safely resolve a string to an HttpMethod
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
