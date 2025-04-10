package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.enums.ExecutionStatus;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an intent that has been executed, along with its status, result, and any error information.
 * This is a simplified version of ExecutionRecord used primarily for feedback to the LLM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An intent that has been executed, with status and result information")
public class ExecutedIntent implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "The name of the intent/tool that was executed")
    private String intent;

    @Schema(description = "Status of the execution")
    private ExecutionStatus status;

    @Schema(description = "Result of the execution (if successful)")
    private Object result;

    @Schema(description = "Error message (if execution failed)")
    private String error;

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
     * Checks if the execution failed.
     *
     * @return true if the execution failed, false otherwise
     */
    @JsonIgnore
    public boolean hasFailed() {
        return status == ExecutionStatus.FAILED_RETRYABLE || status == ExecutionStatus.FAILED_PERMANENT;
    }

    /**
     * Checks if the execution can be retried.
     *
     * @return true if the execution can be retried, false otherwise
     */
    @JsonIgnore
    public boolean canRetry() {
        return status == ExecutionStatus.FAILED_RETRYABLE;
    }

    /**
     * Gets a specific field from the result object.
     *
     * @param <T> The expected type of the result field
     * @param fieldName The name of the field to get
     * @param type The class of the expected type
     * @return The field value, or null if not found or not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getResultField(String fieldName, Class<T> type) {
        if (result == null || !(result instanceof Map)) {
            return null;
        }

        Map<String, Object> resultMap = (Map<String, Object>) result;
        Object value = resultMap.get(fieldName);

        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return (T) value;
        }

        return null;
    }

    /**
     * Gets a string representation of the result for human consumption.
     *
     * @return A formatted string representation of the result
     */
    @JsonIgnore
    public String getFormattedResult() {
        if (result == null) {
            return "No result";
        }

        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(entry.getKey()).append(": ");

                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value);
                }
            }

            return sb.toString();
        }

        return result.toString();
    }

    /**
     * Creates a new ExecutedIntent from an execution record.
     *
     * @param record The execution record to convert
     * @return A new ExecutedIntent
     */
    public static ExecutedIntent fromExecutionRecord(ExecutionRecord record) {
        return ExecutedIntent.builder()
                .intent(record.getIntent())
                .status(record.getStatus())
                .result(record.getResult())
                .error(record.getError())
                .build();
    }
    /**
     * Gets the parameters as a map from the result object.
     * This is a helper method for accessing parameter information stored in results.
     *
     * @return The parameters map, or an empty map if not available
     */
    @JsonIgnore
    public Map<String, Object> getParameterMap() {
        if (this.getResult() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) this.getResult();
            if (resultMap.containsKey("parameters")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) resultMap.get("parameters");
                return params;
            }
        }
        return new HashMap<>();
    }
}