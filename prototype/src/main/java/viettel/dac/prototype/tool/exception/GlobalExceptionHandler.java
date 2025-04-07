package viettel.dac.prototype.tool.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ToolAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleToolAlreadyExists(ToolAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "TOOL_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleToolNotFound(ToolNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DependencyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDependencyNotFound(DependencyNotFoundException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "DEPENDENCY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(CircularDependencyException.class)
    public ResponseEntity<ErrorResponse> handleCircularDependency(CircularDependencyException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage());
    }

    @ExceptionHandler(InvalidToolDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToolDefinition(InvalidToolDefinitionException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TOOL_DEFINITION", ex.getMessage());
    }

    // Helper method to build error responses
    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String code, String message) {
        ErrorResponse error = new ErrorResponse(
                status.value(),
                code,
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // Error response structure for consistency in API responses
    record ErrorResponse(int status, String code, String message, LocalDateTime timestamp) {}
}

