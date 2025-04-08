package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;
import viettel.dac.prototype.execution.enums.ExecutionState;
import viettel.dac.prototype.execution.exception.MissingParameterException;
import viettel.dac.prototype.execution.exception.ParameterValidationException;

import java.io.Serializable;
import java.util.*;

/**
 * Represents an intent detected by the LLM with its parameters.
 * An intent corresponds to a specific tool to be executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "An intent detected by the LLM with its parameters")
public class Intent implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "The name of the intent (tool to execute)", example = "GetWeather")
    @NotBlank(message = "Intent name is required")
    @Size(max = 100, message = "Intent name must not exceed 100 characters")
    private String intent;

    @Schema(description = "Confidence score for this intent", example = "0.92")
    @Range(min = 0, max = 1, message = "Confidence must be between 0 and 1")
    private double confidence;

    @Schema(description = "Parameters for the intent execution")
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    @Schema(description = "Current execution state of this intent")
    @Builder.Default
    private ExecutionState state = ExecutionState.PENDING;

    /**
     * Validates that all required parameters for this intent are present.
     *
     * @param requiredParams List of parameter names that are required
     * @throws MissingParameterException if a required parameter is missing
     */
    public void validateRequiredParameters(List<String> requiredParams) {
        List<String> missingParams = new ArrayList<>();

        for (String param : requiredParams) {
            if (!parameters.containsKey(param) || parameters.get(param) == null) {
                missingParams.add(param);
            }
        }

        if (!missingParams.isEmpty()) {
            if (missingParams.size() == 1) {
                throw new MissingParameterException(missingParams.get(0));
            } else {
                throw new MissingParameterException("Missing required parameters: " +
                        String.join(", ", missingParams));
            }
        }
    }

    /**
     * Validates a parameter value against an expected type.
     *
     * @param paramName The name of the parameter to validate
     * @param expectedType The expected Java type of the parameter
     * @throws ParameterValidationException if the parameter value doesn't match the expected type
     */
    public void validateParameterType(String paramName, Class<?> expectedType) {
        Object value = parameters.get(paramName);

        if (value != null && !expectedType.isInstance(value)) {
            throw new ParameterValidationException(
                    String.format("Parameter '%s' must be of type %s, but was %s",
                            paramName, expectedType.getSimpleName(), value.getClass().getSimpleName())
            );
        }
    }

    /**
     * Adds a parameter to this intent.
     *
     * @param name The parameter name
     * @param value The parameter value
     * @return This intent instance for method chaining
     */
    public Intent addParameter(String name, Object value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Gets a parameter value cast to the expected type.
     *
     * @param <T> The expected parameter type
     * @param name The parameter name
     * @param type The class of the expected type
     * @return The parameter value cast to the expected type
     * @throws ClassCastException if the parameter value cannot be cast to the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameterAs(String name, Class<T> type) {
        Object value = parameters.get(name);
        if (value == null) {
            return null;
        }

        try {
            return (T) value;
        } catch (ClassCastException e) {
            throw new ParameterValidationException(
                    String.format("Parameter '%s' cannot be cast to %s",
                            name, type.getSimpleName())
            );
        }
    }

    /**
     * Checks if this intent is ready to be executed.
     *
     * @return true if the intent is in the PENDING state, false otherwise
     */
    @JsonIgnore
    public boolean isReadyToExecute() {
        return state == ExecutionState.PENDING;
    }

    /**
     * Checks if this intent has completed execution.
     *
     * @return true if the intent is in the COMPLETED state, false otherwise
     */
    @JsonIgnore
    public boolean isCompleted() {
        return state == ExecutionState.COMPLETED;
    }

    /**
     * Checks if this intent has failed execution.
     *
     * @return true if the intent is in the FAILED state, false otherwise
     */
    @JsonIgnore
    public boolean hasFailed() {
        return state == ExecutionState.FAILED;
    }
}