package viettel.dac.prototype.llm.exception;

/**
 * Exception thrown when there is an error processing a chat message.
 */
public class ChatProcessingException extends RuntimeException {
    public ChatProcessingException(String message) {
        super(message);
    }

    public ChatProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}