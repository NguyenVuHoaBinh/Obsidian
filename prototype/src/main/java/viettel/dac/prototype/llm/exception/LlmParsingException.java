package viettel.dac.prototype.llm.exception;

/**
 * Exception thrown when there is an error parsing the LLM response.
 */
public class LlmParsingException extends RuntimeException {
    public LlmParsingException(String message) {
        super(message);
    }

    public LlmParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}