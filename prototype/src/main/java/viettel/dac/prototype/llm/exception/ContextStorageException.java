package viettel.dac.prototype.llm.exception;

/**
 * Exception thrown when there is an error storing or retrieving conversation context.
 */
public class ContextStorageException extends RuntimeException {
    public ContextStorageException(String message) {
        super(message);
    }

    public ContextStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}