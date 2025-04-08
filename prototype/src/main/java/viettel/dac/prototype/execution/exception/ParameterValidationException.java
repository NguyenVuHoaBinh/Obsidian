package viettel.dac.prototype.execution.exception;

/**
 * Exception thrown when a parameter fails validation during tool execution.
 */
public class ParameterValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String parameterName;
    private final String expectedType;
    private final Object actualValue;

    /**
     * Constructs a new ParameterValidationException with the specified error message.
     *
     * @param message The error message
     */
    public ParameterValidationException(String message) {
        super(message);
        this.parameterName = null;
        this.expectedType = null;
        this.actualValue = null;
    }

    /**
     * Constructs a new ParameterValidationException with detailed information about the validation failure.
     *
     * @param parameterName The name of the parameter that failed validation
     * @param expectedType The expected type or format
     * @param actualValue The actual value that was provided
     */
    public ParameterValidationException(String parameterName, String expectedType, Object actualValue) {
        super(String.format("Parameter '%s' validation failed: expected %s but got %s",
                parameterName, expectedType, actualValue));
        this.parameterName = parameterName;
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }

    /**
     * Constructs a new ParameterValidationException with a custom message and additional details.
     *
     * @param message The custom error message
     * @param parameterName The name of the parameter that failed validation
     */
    public ParameterValidationException(String message, String parameterName) {
        super(message);
        this.parameterName = parameterName;
        this.expectedType = null;
        this.actualValue = null;
    }

    /**
     * Gets the name of the parameter that failed validation.
     *
     * @return The parameter name
     */
    public String getParameterName() {
        return parameterName;
    }

    /**
     * Gets the expected type or format for the parameter.
     *
     * @return The expected type or format
     */
    public String getExpectedType() {
        return expectedType;
    }

    /**
     * Gets the actual value that was provided.
     *
     * @return The actual value
     */
    public Object getActualValue() {
        return actualValue;
    }

    /**
     * Creates a new ParameterValidationException for a type mismatch error.
     *
     * @param parameterName The name of the parameter
     * @param expectedType The expected Java type
     * @param actualType The actual Java type
     * @return A new ParameterValidationException
     */
    public static ParameterValidationException typeMismatch(String parameterName, Class<?> expectedType, Class<?> actualType) {
        return new ParameterValidationException(
                parameterName,
                expectedType.getSimpleName(),
                actualType.getSimpleName()
        );
    }

    /**
     * Creates a new ParameterValidationException for a range error.
     *
     * @param parameterName The name of the parameter
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @param actualValue The actual value
     * @return A new ParameterValidationException
     */
    public static ParameterValidationException outOfRange(String parameterName, Number min, Number max, Number actualValue) {
        return new ParameterValidationException(
                String.format("Parameter '%s' must be between %s and %s, but was %s",
                        parameterName, min, max, actualValue),
                parameterName
        );
    }

    /**
     * Creates a new ParameterValidationException for a format error.
     *
     * @param parameterName The name of the parameter
     * @param format The expected format description
     * @param actualValue The actual value
     * @return A new ParameterValidationException
     */
    public static ParameterValidationException invalidFormat(String parameterName, String format, String actualValue) {
        return new ParameterValidationException(
                String.format("Parameter '%s' must match format '%s', but was '%s'",
                        parameterName, format, actualValue),
                parameterName
        );
    }
}