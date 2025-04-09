package viettel.dac.prototype.exception;

import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import viettel.dac.prototype.execution.exception.*;
import viettel.dac.prototype.tool.exception.DependencyNotFoundException;
import viettel.dac.prototype.tool.exception.InvalidToolDefinitionException;
import viettel.dac.prototype.tool.exception.ToolAlreadyExistsException;
import viettel.dac.prototype.tool.exception.ToolExecutionException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified global exception handler for the application.
 * Handles both tool and execution related exceptions.
 */
@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class UnifiedGlobalExceptionHandler {

    private final MeterRegistry meterRegistry;
    private final Environment environment;

    // ==================== TOOL EXCEPTIONS ====================

    @ExceptionHandler(ToolAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleToolAlreadyExists(ToolAlreadyExistsException ex, WebRequest request) {
        log.warn("Tool already exists: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "TOOL_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(viettel.dac.prototype.tool.exception.ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleToolNotFound(viettel.dac.prototype.tool.exception.ToolNotFoundException ex, WebRequest request) {
        log.warn("Tool not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(DependencyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDependencyNotFound(DependencyNotFoundException ex, WebRequest request) {
        log.warn("Dependency not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "DEPENDENCY_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(viettel.dac.prototype.tool.exception.CircularDependencyException.class)
    public ResponseEntity<ErrorResponse> handleToolCircularDependency(viettel.dac.prototype.tool.exception.CircularDependencyException ex, WebRequest request) {
        log.warn("Circular dependency detected: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidToolDefinitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToolDefinition(InvalidToolDefinitionException ex, WebRequest request) {
        log.warn("Invalid tool definition: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TOOL_DEFINITION", ex.getMessage(), request);
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ErrorResponse> handleToolExecution(ToolExecutionException ex, WebRequest request) {
        log.error("Tool execution failed: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_EXECUTION_FAILED", ex.getMessage(), request);
    }

    // ==================== EXECUTION EXCEPTIONS ====================

    @ExceptionHandler(viettel.dac.prototype.execution.exception.CircularDependencyException.class)
    public ResponseEntity<ErrorResponse> handleExecutionCircularDependency(viettel.dac.prototype.execution.exception.CircularDependencyException ex, WebRequest request) {
        log.error("Circular dependency detected: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "circular_dependency").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "CIRCULAR_DEPENDENCY", ex.getMessage(), request);
    }

    @ExceptionHandler(MissingParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingParameterException ex, WebRequest request) {
        log.error("Missing parameter: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "missing_parameter").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage(), request);
    }

    @ExceptionHandler(ParameterValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(ParameterValidationException ex, WebRequest request) {
        log.error("Parameter validation error: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "parameter_validation").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "PARAMETER_VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(viettel.dac.prototype.execution.exception.ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleExecutionToolNotFound(viettel.dac.prototype.execution.exception.ToolNotFoundException ex, WebRequest request) {
        log.error("Tool not found: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "tool_not_found").increment();
        return buildErrorResponse(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidExecutionOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidExecutionOrder(InvalidExecutionOrderException ex, WebRequest request) {
        log.error("Invalid execution order: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "invalid_execution_order").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_EXECUTION_ORDER", ex.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedHttpMethodException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedHttpMethod(UnsupportedHttpMethodException ex, WebRequest request) {
        log.error("Unsupported HTTP method: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "unsupported_http_method").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "UNSUPPORTED_HTTP_METHOD", ex.getMessage(), request);
    }

    @ExceptionHandler(ExecutionFailureException.class)
    public ResponseEntity<ErrorResponse> handleExecutionFailure(ExecutionFailureException ex, WebRequest request) {
        log.error("Execution failure: {}", ex.getMessage(), ex);
        meterRegistry.counter("execution.errors", "type", "execution_failure").increment();
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "EXECUTION_FAILURE", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        log.error("Invalid request: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "invalid_request").increment();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request);
    }

    @ExceptionHandler(MissingDependencyException.class)
    public ResponseEntity<ErrorResponse> handleMissingDependency(MissingDependencyException ex, WebRequest request) {
        log.error("Missing dependencies detected: {}", ex.getMessage());
        meterRegistry.counter("execution.errors", "type", "missing_dependency").increment();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_DEPENDENCIES",
                ex.getMessage(),
                request
        );
    }

    // ==================== COMMON EXCEPTIONS ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        log.error("Validation error: {}", errors.toString());
        meterRegistry.counter("app.errors", "type", "validation").increment();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed: " + String.join(", ", errors),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());

        log.error("Constraint violation: {}", errors.toString());
        meterRegistry.counter("app.errors", "type", "constraint_violation").increment();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "Validation failed: " + String.join(", ", errors),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String error = ex.getName() + " should be of type " + ex.getRequiredType().getName();
        log.error("Method argument type mismatch: {}", error);
        meterRegistry.counter("app.errors", "type", "type_mismatch").increment();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "TYPE_MISMATCH",
                error,
                request
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, WebRequest request) {
        String error = ex.getParameterName() + " parameter is missing";
        log.error("Missing request parameter: {}", error);
        meterRegistry.counter("app.errors", "type", "missing_parameter").increment();

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                error,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        meterRegistry.counter("app.errors", "type", "unhandled").increment();

        String errorMessage = isProductionProfile() ?
                "An unexpected error occurred. Please try again later." :
                ex.getMessage();

        String errorId = UUID.randomUUID().toString();
        log.error("Error ID: {} - Details: {}", errorId, ex.toString(), ex);

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                errorMessage,
                request,
                errorId
        );
    }

    // ==================== HELPER METHODS ====================

    // Helper method to build error responses
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            WebRequest request) {
        return buildErrorResponse(status, code, message, request, null);
    }

    // Helper method to build error responses with an error ID
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            WebRequest request,
            String errorId) {

        // Get request details for logging
        String path = request instanceof ServletWebRequest ?
                ((ServletWebRequest)request).getRequest().getRequestURI() : "unknown";

        log.debug("Returning error response: {} {} for path: {}", status, code, path);

        ErrorResponse error = new ErrorResponse(
                status.value(),
                code,
                message,
                path,
                errorId,
                LocalDateTime.now()
        );

        return new ResponseEntity<>(error, status);
    }

    // Check if running in production profile
    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Standard error response structure for the API.
     */
    @Schema(description = "Standard error response")
    public static class ErrorResponse {
        @Schema(description = "HTTP status code", example = "400")
        private final int status;

        @Schema(description = "Error code", example = "MISSING_PARAMETER")
        private final String code;

        @Schema(description = "Error message", example = "Missing required parameter: city")
        private final String message;

        @Schema(description = "Path that generated the error", example = "/api/execute/abc-123")
        private final String path;

        @Schema(description = "Unique error ID for tracking", example = "550e8400-e29b-41d4-a716-446655440000")
        private final String errorId;

        @Schema(description = "Timestamp when the error occurred")
        private final LocalDateTime timestamp;

        @Schema(description = "Additional error details", nullable = true)
        private List<ErrorDetail> details;

        public ErrorResponse(int status, String code, String message, String path, String errorId, LocalDateTime timestamp) {
            this.status = status;
            this.code = code;
            this.message = message;
            this.path = path;
            this.errorId = errorId;
            this.timestamp = timestamp;
            this.details = new ArrayList<>();
        }

        // Getters - needed for serialization
        public int getStatus() { return status; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getPath() { return path; }
        public String getErrorId() { return errorId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public List<ErrorDetail> getDetails() { return details; }

        // Builder methods
        public ErrorResponse addDetail(String field, String message) {
            if (details == null) {
                details = new ArrayList<>();
            }
            details.add(new ErrorDetail(field, message));
            return this;
        }
    }

    /**
     * Detailed error information for field-specific errors.
     */
    @Schema(description = "Detailed error information for a specific field")
    public static class ErrorDetail {
        @Schema(description = "Field name that caused the error", example = "city")
        private final String field;

        @Schema(description = "Error message for the field", example = "must not be blank")
        private final String message;

        public ErrorDetail(String field, String message) {
            this.field = field;
            this.message = message;
        }

        // Getters - needed for serialization
        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}