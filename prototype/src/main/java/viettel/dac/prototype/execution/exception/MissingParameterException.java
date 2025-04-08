package viettel.dac.prototype.execution.exception;

import java.util.Arrays;
import java.util.List;

/**
 * Exception thrown when a required parameter is missing during tool execution.
 */
public class MissingParameterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<String> parameterNames;

    /**
     * Constructs a new MissingParameterException with the specified parameter name.
     *
     * @param parameterName The name of the missing parameter
     */
    public MissingParameterException(String parameterName) {
        super("Missing required parameter: " + parameterName);
        this.parameterNames = Arrays.asList(parameterName);
    }

    /**
     * Constructs a new MissingParameterException with multiple missing parameter names.
     *
     * @param parameterNames List of missing parameter names
     */
    public MissingParameterException(List<String> parameterNames) {
        super("Missing required parameters: " + String.join(", ", parameterNames));
        this.parameterNames = parameterNames;
    }

    /**
     * Constructs a new MissingParameterException with multiple missing parameter names.
     *
     * @param message Custom error message
     * @param parameterNames List of missing parameter names
     */
    public MissingParameterException(String message, List<String> parameterNames) {
        super(message);
        this.parameterNames = parameterNames;
    }

    /**
     * Gets the names of the missing parameters.
     *
     * @return List of missing parameter names
     */
    public List<String> getParameterNames() {
        return parameterNames;
    }

    /**
     * Checks if a specific parameter name is among the missing parameters.
     *
     * @param parameterName The parameter name to check
     * @return true if the parameter is missing, false otherwise
     */
    public boolean isMissingParameter(String parameterName) {
        return parameterNames.contains(parameterName);
    }

    /**
     * Gets the count of missing parameters.
     *
     * @return The number of missing parameters
     */
    public int getMissingParameterCount() {
        return parameterNames.size();
    }
}