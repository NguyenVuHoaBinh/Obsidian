package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.enums.ExecutionStatus;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a record of a tool execution including parameters, results, timing, and status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Record of a tool execution")
public class ExecutionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Name of the intent/tool that was executed")
    private String intent;

    @Schema(description = "Status of the execution")
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Schema(description = "Parameters passed to the tool")
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    @Schema(description = "Result returned by the tool")
    private Map<String, Object> result;

    @Schema(description = "Error message if execution failed")
    private String error;

    @Schema(description = "Time when execution started")
    private LocalDateTime startTime;

    @Schema(description = "Time when execution completed")
    private LocalDateTime endTime;

    @Schema(description = "Duration of execution in milliseconds")
    private long durationMillis;

    @Schema(description = "Number of retry attempts made")
    private int retryCount;

    /**
     * Checks if the execution was successful.
     *
     * @return true if the execution completed successfully, false otherwise
     */
    @JsonIgnore
    public boolean isSuccessful() {
        return status == ExecutionStatus.COMPLETED;
    }

    /**
     * Checks if the execution has failed.
     *
     * @return true if the execution failed (either retryable or permanent), false otherwise
     */
    @JsonIgnore
    public boolean hasFailed() {
        return status == ExecutionStatus.FAILED_RETRYABLE || status == ExecutionStatus.FAILED_PERMANENT;
    }

    /**
     * Checks if the execution can be retried.
     *
     * @return true if the execution failed but can be retried, false otherwise
     */
    @JsonIgnore
    public boolean canRetry() {
        return status == ExecutionStatus.FAILED_RETRYABLE;
    }

    /**
     * Gets the duration of the execution as a Duration object.
     *
     * @return Duration object representing the execution time
     */
    @JsonIgnore
    public Duration getDuration() {
        return Duration.ofMillis(durationMillis);
    }

    /**
     * Gets a parameter value of a specific type.
     *
     * @param <T> The expected type of the parameter
     * @param name The parameter name
     * @param type The class of the expected type
     * @return The parameter value cast to the expected type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameterAs(String name, Class<T> type) {
        if (parameters == null || !parameters.containsKey(name)) {
            return null;
        }

        Object value = parameters.get(name);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        throw new ClassCastException("Parameter '" + name + "' cannot be cast to " + type.getSimpleName());
    }

    /**
     * Gets a result value of a specific type.
     *
     * @param <T> The expected type of the result value
     * @param name The result field name
     * @param type The class of the expected type
     * @return The result value cast to the expected type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getResultAs(String name, Class<T> type) {
        if (result == null || !result.containsKey(name)) {
            return null;
        }

        Object value = result.get(name);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        throw new ClassCastException("Result field '" + name + "' cannot be cast to " + type.getSimpleName());
    }

    /**
     * Increments the retry count and returns the new value.
     *
     * @return The new retry count
     */
    public int incrementRetryCount() {
        return ++retryCount;
    }

    /**
     * Sets a new parameter value or updates an existing one.
     *
     * @param name The parameter name
     * @param value The parameter value
     * @return This record instance for method chaining
     */
    public ExecutionRecord setParameter(String name, Object value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(name, value);
        return this;
    }

    /**
     * Creates a copy of this record with a new status and error message.
     * Useful for tracking retry attempts.
     *
     * @param newStatus The new execution status
     * @param errorMessage The error message if failed
     * @return A new ExecutionRecord with updated status
     */
    public ExecutionRecord copyWithStatus(ExecutionStatus newStatus, String errorMessage) {
        ExecutionRecord copy = new ExecutionRecord();
        copy.intent = this.intent;
        copy.parameters = new HashMap<>(this.parameters);
        copy.status = newStatus;
        copy.error = errorMessage;
        copy.retryCount = this.retryCount + 1;
        copy.startTime = LocalDateTime.now();
        return copy;
    }
}