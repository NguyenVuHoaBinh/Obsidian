package viettel.dac.prototype.execution.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents the status of a tool execution.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
@Schema(description = "Status of a tool execution")
public enum ExecutionStatus {
    /**
     * Execution has not yet started
     */
    PENDING("Pending", false, false, 0),

    /**
     * Execution is currently in progress
     */
    IN_PROGRESS("In Progress", false, false, 1),

    /**
     * Execution completed successfully
     */
    COMPLETED("Completed", true, true, 2),

    /**
     * Execution failed but can be retried
     */
    FAILED_RETRYABLE("Failed (Retryable)", false, true, 3),

    /**
     * Execution failed and cannot be retried
     */
    FAILED_PERMANENT("Failed (Permanent)", false, true, 4);

    private final String displayName;
    private final boolean successful;
    private final boolean terminal;
    private final int order;

    /**
     * Constructor for ExecutionStatus.
     *
     * @param displayName Human-readable name for display purposes
     * @param successful Whether this status indicates a successful execution
     * @param terminal Whether this status indicates the execution is complete (no further state changes expected)
     * @param order The ordering value used for sorting
     */
    ExecutionStatus(String displayName, boolean successful, boolean terminal, int order) {
        this.displayName = displayName;
        this.successful = successful;
        this.terminal = terminal;
        this.order = order;
    }

    /**
     * Gets the human-readable display name for this status.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this status indicates a successful execution.
     *
     * @return true if this status indicates success, false otherwise
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * Checks if this status represents a terminal state (no further state changes expected).
     *
     * @return true if this is a terminal state, false otherwise
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Gets the order value for sorting purposes.
     *
     * @return The order value
     */
    public int getOrder() {
        return order;
    }

    /**
     * Checks if this status indicates a failed execution.
     *
     * @return true if this status represents a failure, false otherwise
     */
    public boolean isFailed() {
        return this == FAILED_RETRYABLE || this == FAILED_PERMANENT;
    }

    /**
     * Checks if this status allows for retry attempts.
     *
     * @return true if the execution can be retried, false otherwise
     */
    public boolean canRetry() {
        return this == FAILED_RETRYABLE;
    }

    /**
     * Checks if this status indicates that execution is active.
     *
     * @return true if execution is active, false otherwise
     */
    public boolean isActive() {
        return this == IN_PROGRESS;
    }

    /**
     * Checks if this status indicates that execution is pending.
     *
     * @return true if execution is pending, false otherwise
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Converts this ExecutionStatus to the corresponding ExecutionState.
     *
     * @return The corresponding ExecutionState
     */
    public ExecutionState toExecutionState() {
        if (this == COMPLETED) {
            return ExecutionState.COMPLETED;
        } else if (this == FAILED_RETRYABLE || this == FAILED_PERMANENT) {
            return ExecutionState.FAILED;
        } else {
            return ExecutionState.PENDING;
        }
    }
}