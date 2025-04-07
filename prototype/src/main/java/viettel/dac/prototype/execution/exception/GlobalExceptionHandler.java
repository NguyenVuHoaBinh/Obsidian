package viettel.dac.prototype.execution.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CircularDependencyException.class)
    public ResponseEntity<ErrorResponse> handleCircularDependency(CircularDependencyException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage());
    }

    @ExceptionHandler(MissingParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingParameterException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage());
    }

    @ExceptionHandler(ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleToolNotFound(ToolNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidExecutionOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidExecutionOrder(InvalidExecutionOrderException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_EXECUTION_ORDER", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedHttpMethodException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedHttpMethod(UnsupportedHttpMethodException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "UNSUPPORTED_HTTP_METHOD", ex.getMessage());
    }

    @ExceptionHandler(ExecutionFailureException.class)
    public ResponseEntity<ErrorResponse> handleExecutionFailure(ExecutionFailureException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "EXECUTION_FAILURE", ex.getMessage());
    }

    // Generic handler for all other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ex.getMessage());
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

    // Error response structure
    public record ErrorResponse(int status, String code, String message, LocalDateTime timestamp) {}
}

