package viettel.dac.prototype.tool.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ToolAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleToolAlreadyExists(ToolAlreadyExistsException ex, WebRequest request) {
        logger.warn("Tool already exists: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "TOOL_ALREADY_EXISTS", ex.getMessage(), null);
    }

    @ExceptionHandler(ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleToolNotFound(ToolNotFoundException ex, WebRequest request) {
        logger.warn("Tool not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(DependencyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDependencyNotFound(DependencyNotFoundException ex, WebRequest request) {
        logger.warn("Dependency not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "DEPENDENCY_NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(CircularDependencyException.class)
    public ResponseEntity<ErrorResponse> handleCircularDependency(CircularDependencyException ex, WebRequest request) {
        logger.warn("Circular dependency detected: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidToolDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToolDefinition(InvalidToolDefinitionException ex, WebRequest request) {
        logger.warn("Invalid tool definition: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TOOL_DEFINITION", ex.getMessage(), null);
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ErrorResponse> handleToolExecution(ToolExecutionException ex, WebRequest request) {
        logger.error("Tool execution failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_EXECUTION_FAILED", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        logger.warn("Validation failed: {}", validationErrors);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                validationErrors
        );
    }

    // Generic handler for all other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later or contact support.",
                null
        );
    }

    // Helper method to build error responses
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String code, String message, Map<String, String> details) {
        ErrorResponse error = new ErrorResponse(
                status.value(),
                code,
                message,
                details,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // Enhanced error response structure
    public static class ErrorResponse {
        private final int status;
        private final String code;
        private final String message;
        private final Map<String, String> details;
        private final LocalDateTime timestamp;

        public ErrorResponse(int status, String code, String message, Map<String, String> details, LocalDateTime timestamp) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.details = details;
            this.timestamp = timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, String> getDetails() {
            return details;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}