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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the result of executing tools based on an analysis result.
 * Contains detailed records of each tool execution and summary statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of executing tools based on an analysis result")
public class ExecutionResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID of the analysis that was executed")
    private String analysisId;

    @Schema(description = "Detailed records of each tool execution")
    @Builder.Default
    private List<ExecutionRecord> executionRecords = new ArrayList<>();

    @Schema(description = "Timestamp when the execution was performed")
    private LocalDateTime executionTime = LocalDateTime.now();

    @Schema(description = "Summary statistics of the execution")
    private ExecutionSummary summary;

    /**
     * Returns a summary of errors from all execution records.
     *
     * @return A string containing all error messages, one per line
     */
    @JsonIgnore
    public String getErrorSummary() {
        return executionRecords.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.FAILED_RETRYABLE ||
                        r.getStatus() == ExecutionStatus.FAILED_PERMANENT)
                .map(r -> String.format("Intent '%s' failed: %s", r.getIntent(), r.getError()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Checks if the execution was completely successful.
     *
     * @return true if all intents were executed successfully, false otherwise
     */
    @JsonIgnore
    public boolean isSuccessful() {
        return summary != null && summary.getFailed() == 0;
    }

    /**
     * Gets the total execution time across all records.
     *
     * @return The total execution time in milliseconds
     */
    @JsonIgnore
    public long getTotalExecutionTimeMs() {
        return executionRecords.stream()
                .mapToLong(ExecutionRecord::getDurationMillis)
                .sum();
    }

    /**
     * Gets the execution duration as a Duration object.
     *
     * @return The execution duration
     */
    @JsonIgnore
    public Duration getExecutionDuration() {
        return Duration.ofMillis(getTotalExecutionTimeMs());
    }

    /**
     * Gets a mapping of intent names to their execution status.
     *
     * @return A map of intent names to execution status
     */
    @JsonIgnore
    public Map<String, ExecutionStatus> getStatusMap() {
        return executionRecords.stream()
                .collect(Collectors.toMap(
                        ExecutionRecord::getIntent,
                        ExecutionRecord::getStatus
                ));
    }

    /**
     * Gets all successfully executed intents.
     *
     * @return A list of execution records for successfully executed intents
     */
    @JsonIgnore
    public List<ExecutionRecord> getSuccessfulExecutions() {
        return executionRecords.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /**
     * Gets all failed intent executions.
     *
     * @return A list of execution records for failed intents
     */
    @JsonIgnore
    public List<ExecutionRecord> getFailedExecutions() {
        return executionRecords.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.FAILED_PERMANENT ||
                        r.getStatus() == ExecutionStatus.FAILED_RETRYABLE)
                .collect(Collectors.toList());
    }

    /**
     * Gets intents that could potentially be retried.
     *
     * @return A list of execution records for intents that could be retried
     */
    @JsonIgnore
    public List<ExecutionRecord> getRetryableExecutions() {
        return executionRecords.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.FAILED_RETRYABLE)
                .collect(Collectors.toList());
    }
}