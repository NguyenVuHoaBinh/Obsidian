package viettel.dac.prototype.llm.exception;

/**
 * Exception thrown when there is an error communicating with the LLM API.
 */
public class LlmApiException extends RuntimeException {
    public LlmApiException(String message) {
        super(message);
    }

    public LlmApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
