package viettel.dac.prototype.execution.exception;

public class MissingParameterException extends RuntimeException {
    public MissingParameterException(String parameterName) {
        super("Missing required parameter: " + parameterName);
    }
}
