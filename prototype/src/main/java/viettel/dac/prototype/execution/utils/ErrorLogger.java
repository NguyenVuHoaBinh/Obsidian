package viettel.dac.prototype.execution.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorLogger {

    private static final Logger logger = LoggerFactory.getLogger(ErrorLogger.class);

    /**
     * Logs an error message with details about the exception.
     *
     * @param message The error message to log.
     * @param e       The exception that occurred.
     */
    public static void logError(String message, Exception e) {
        logger.error(message + ": " + e.getMessage(), e);
    }
}
