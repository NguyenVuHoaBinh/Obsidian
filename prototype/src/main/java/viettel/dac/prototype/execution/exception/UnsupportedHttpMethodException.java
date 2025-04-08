package viettel.dac.prototype.execution.exception;

import viettel.dac.prototype.tool.enums.HttpMethodType;

public class UnsupportedHttpMethodException extends RuntimeException {
    /**
     * Constructor that accepts a String representation of the HTTP method.
     *
     * @param httpMethod The HTTP method as a String
     */
    public UnsupportedHttpMethodException(String httpMethod) {
        super("Unsupported HTTP method: " + httpMethod);
    }

    /**
     * Constructor that accepts an HttpMethodType enum.
     *
     * @param httpMethod The HTTP method as an enum
     */
    public UnsupportedHttpMethodException(HttpMethodType httpMethod) {
        super("Unsupported HTTP method: " + httpMethod.name());
    }
}