package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.utils.ExecutionUtils;

import java.io.Serializable;

/**
 * Contains summary statistics for a set of tool executions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary statistics for tool executions")
public class ExecutionSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Total number of intents/tools that were executed")
    private int totalIntents;

    @Schema(description = "Number of intents that completed successfully")
    private int completed;

    @Schema(description = "Number of intents that failed")
    private int failed;

    @Schema(description = "Average execution duration in milliseconds")
    private double averageDuration;

    @Schema(description = "Flag indicating if there were any errors")
    private boolean hasErrors;

    @Schema(description = "Fastest execution time in milliseconds")
    private long fastestExecution;

    @Schema(description = "Slowest execution time in milliseconds")
    private long slowestExecution;

    @Schema(description = "Total execution time in milliseconds")
    private long totalDuration;

    /**
     * Gets the success rate as a percentage.
     *
     * @return The percentage of successful executions (0-100)
     */
    @JsonIgnore
    public double getSuccessRate() {
        if (totalIntents == 0) {
            return 0.0;
        }
        return (double) completed / totalIntents * 100.0;
    }

    /**
     * Gets the formatted average duration.
     *
     * @return A human-readable string of the average duration
     */
    @JsonIgnore
    public String getFormattedAverageDuration() {
        return ExecutionUtils.formatDurationDetailed((long) averageDuration);
    }

    /**
     * Gets the formatted total duration.
     *
     * @return A human-readable string of the total duration
     */
    @JsonIgnore
    public String getFormattedTotalDuration() {
        return ExecutionUtils.formatDurationDetailed(totalDuration);
    }

    /**
     * Gets the formatted fastest execution time.
     *
     * @return A human-readable string of the fastest execution time
     */
    @JsonIgnore
    public String getFormattedFastestExecution() {
        return ExecutionUtils.formatDurationDetailed(fastestExecution);
    }

    /**
     * Gets the formatted slowest execution time.
     *
     * @return A human-readable string of the slowest execution time
     */
    @JsonIgnore
    public String getFormattedSlowestExecution() {
        return ExecutionUtils.formatDurationDetailed(slowestExecution);
    }

    /**
     * Checks if all intents were completed successfully.
     *
     * @return true if all intents completed successfully, false otherwise
     */
    @JsonIgnore
    public boolean isFullySuccessful() {
        return totalIntents > 0 && completed == totalIntents;
    }

    /**
     * Checks if all intents failed.
     *
     * @return true if all intents failed, false otherwise
     */
    @JsonIgnore
    public boolean isCompleteFailure() {
        return totalIntents > 0 && failed == totalIntents;
    }

    /**
     * Gets a short textual summary of the execution.
     *
     * @return A string summarizing the execution results
     */
    @JsonIgnore
    public String getShortSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(completed)
                .append("/")
                .append(totalIntents)
                .append(" intents completed successfully");

        if (hasErrors) {
            summary.append(" (")
                    .append(failed)
                    .append(" failed)");
        }

        summary.append(" in ")
                .append(ExecutionUtils.formatDurationDetailed(totalDuration));

        return summary.toString();
    }

    /**
     * Merges another summary into this one.
     * Useful for combining results from parallel executions.
     *
     * @param other The other summary to merge
     * @return This summary after merging
     */
    public ExecutionSummary merge(ExecutionSummary other) {
        if (other == null) {
            return this;
        }

        int newTotalIntents = this.totalIntents + other.totalIntents;
        int newCompleted = this.completed + other.completed;
        int newFailed = this.failed + other.failed;

        this.totalIntents = newTotalIntents;
        this.completed = newCompleted;
        this.failed = newFailed;
        this.hasErrors = this.hasErrors || other.hasErrors;

        // Calculate new average duration
        this.totalDuration += other.totalDuration;
        if (newCompleted > 0) {
            this.averageDuration = (double) this.totalDuration / newCompleted;
        }

        // Update fastest/slowest metrics
        if (other.fastestExecution > 0) {
            if (this.fastestExecution == 0 || other.fastestExecution < this.fastestExecution) {
                this.fastestExecution = other.fastestExecution;
            }
        }

        if (other.slowestExecution > this.slowestExecution) {
            this.slowestExecution = other.slowestExecution;
        }

        return this;
    }
}