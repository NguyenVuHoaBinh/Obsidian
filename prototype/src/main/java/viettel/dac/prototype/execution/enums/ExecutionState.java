package viettel.dac.prototype.execution.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the high-level state of an intent execution.
 * This is a simplified version of ExecutionStatus used primarily for intent tracking.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
@Schema(description = "High-level state of an intent execution")
public enum ExecutionState {
    /**
     * Intent has not yet been executed or is in progress
     */
    PENDING(0, "Not yet executed or in progress"),

    /**
     * Intent was successfully executed
     */
    COMPLETED(1, "Successfully executed"),

    /**
     * Intent execution failed
     */
    FAILED(2, "Execution failed");

    private final int code;
    private final String description;

    /**
     * Constructor for ExecutionState.
     *
     * @param code Numeric code for this state
     * @param description Descriptive text for this state
     */
    ExecutionState(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the numeric code for this state.
     *
     * @return The numeric code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the description for this state.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Converts an ExecutionStatus to the corresponding ExecutionState.
     *
     * @param status The ExecutionStatus to convert
     * @return The corresponding ExecutionState
     */
    public static ExecutionState fromStatus(ExecutionStatus status) {
        if (status == ExecutionStatus.COMPLETED) {
            return COMPLETED;
        } else if (status == ExecutionStatus.FAILED_PERMANENT || status == ExecutionStatus.FAILED_RETRYABLE) {
            return FAILED;
        } else {
            return PENDING;
        }
    }

    /**
     * Checks if this state indicates a completed execution.
     *
     * @return true if the execution is completed, false otherwise
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * Checks if this state indicates a failed execution.
     *
     * @return true if the execution failed, false otherwise
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * Checks if this state indicates a pending or in-progress execution.
     *
     * @return true if the execution is pending or in progress, false otherwise
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Checks if this state indicates the execution is complete (either succeeded or failed).
     *
     * @return true if the execution is complete, false otherwise
     */
    public boolean isComplete() {
        return this == COMPLETED || this == FAILED;
    }
}