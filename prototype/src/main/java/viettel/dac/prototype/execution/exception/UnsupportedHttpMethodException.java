package viettel.dac.prototype.execution.exception;

public class UnsupportedHttpMethodException extends RuntimeException {
    public UnsupportedHttpMethodException(String httpMethod) {
        super("Unsupported HTTP method: " + httpMethod);
    }
}
