package viettel.dac.prototype.execution.exception;

/**
 * Exception thrown when a tool execution fails for various reasons.
 * This exception typically wraps the original exception that caused the failure.
 */
public class ExecutionFailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String toolName;
    private final boolean retryable;

    /**
     * Constructs a new ExecutionFailureException with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause The cause of the execution failure
     */
    public ExecutionFailureException(String message, Throwable cause) {
        super(message, cause);
        this.toolName = null;
        this.retryable = determineRetryable(cause);
    }

    /**
     * Constructs a new ExecutionFailureException with the specified detail message, cause, and tool name.
     *
     * @param message The detail message
     * @param cause The cause of the execution failure
     * @param toolName The name of the tool that failed
     */
    public ExecutionFailureException(String message, Throwable cause, String toolName) {
        super(message, cause);
        this.toolName = toolName;
        this.retryable = determineRetryable(cause);
    }

    /**
     * Constructs a new ExecutionFailureException with the specified detail message, cause, tool name, and retryable flag.
     *
     * @param message The detail message
     * @param cause The cause of the execution failure
     * @param toolName The name of the tool that failed
     * @param retryable Whether the failure is retryable
     */
    public ExecutionFailureException(String message, Throwable cause, String toolName, boolean retryable) {
        super(message, cause);
        this.toolName = toolName;
        this.retryable = retryable;
    }

    /**
     * Gets the name of the tool that failed.
     *
     * @return The tool name
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Checks if the failure is retryable.
     *
     * @return true if the failure is retryable, false otherwise
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Determines if an exception is retryable based on its type.
     *
     * @param cause The exception to check
     * @return true if the exception is retryable, false otherwise
     */
    private boolean determineRetryable(Throwable cause) {
        // Connection issues, timeouts, and server errors are usually retryable
        if (cause instanceof java.net.ConnectException ||
                cause instanceof java.net.SocketTimeoutException ||
                cause instanceof org.springframework.web.client.HttpServerErrorException) {
            return true;
        }

        // Client errors, authentication failures, etc. are usually not retryable
        if (cause instanceof org.springframework.web.client.HttpClientErrorException ||
                cause instanceof MissingParameterException) {
            return false;
        }

        // For other exceptions, allow retry by default
        return true;
    }

    /**
     * Creates a new ExecutionFailureException for a client error.
     *
     * @param toolName The name of the tool that failed
     * @param errorMessage The error message
     * @return A new ExecutionFailureException
     */
    public static ExecutionFailureException clientError(String toolName, String errorMessage) {
        return new ExecutionFailureException("Client error when executing tool '" + toolName + "': " + errorMessage,
                null, toolName, false);
    }

    /**
     * Creates a new ExecutionFailureException for a server error.
     *
     * @param toolName The name of the tool that failed
     * @param errorMessage The error message
     * @return A new ExecutionFailureException
     */
    public static ExecutionFailureException serverError(String toolName, String errorMessage) {
        return new ExecutionFailureException("Server error when executing tool '" + toolName + "': " + errorMessage,
                null, toolName, true);
    }

    /**
     * Creates a new ExecutionFailureException for a timeout.
     *
     * @param toolName The name of the tool that failed
     * @param timeoutMs The timeout in milliseconds
     * @return A new ExecutionFailureException
     */
    public static ExecutionFailureException timeout(String toolName, long timeoutMs) {
        return new ExecutionFailureException("Tool '" + toolName + "' execution timed out after " + timeoutMs + "ms",
                null, toolName, true);
    }
}