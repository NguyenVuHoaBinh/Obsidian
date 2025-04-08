package viettel.dac.prototype.execution.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.enums.ExecutionStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Feedback generated from execution results for the LLM component.
 * Contains details about executed intents, successes, failures, and suggestions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Feedback generated from execution results for the LLM")
public class ExecutionFeedback implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID of the analysis that was executed")
    private String analysisId;

    @Schema(description = "List of executed intents with their statuses and results")
    @Builder.Default
    private List<ExecutedIntent> executedIntents = new ArrayList<>();

    @Schema(description = "Flag indicating if all intents were executed successfully")
    private boolean isComplete;

    @Schema(description = "Summary of any errors that occurred")
    private String errorSummary;

    @Schema(description = "Suggestions for remediation if there were failures")
    private List<String> suggestions;

    @Schema(description = "Human-readable summary of the execution")
    private String executionSummary;

    /**
     * Gets a list of all intents that completed successfully.
     *
     * @return List of successfully executed intents
     */
    @JsonIgnore
    public List<ExecutedIntent> getSuccessfulIntents() {
        return executedIntents.stream()
                .filter(intent -> intent.getStatus() == ExecutionStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of all intents that failed.
     *
     * @return List of failed intents
     */
    @JsonIgnore
    public List<ExecutedIntent> getFailedIntents() {
        return executedIntents.stream()
                .filter(intent -> intent.getStatus() == ExecutionStatus.FAILED_PERMANENT ||
                        intent.getStatus() == ExecutionStatus.FAILED_RETRYABLE)
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of all intents that could potentially be retried.
     *
     * @return List of intents that could be retried
     */
    @JsonIgnore
    public List<ExecutedIntent> getRetryableIntents() {
        return executedIntents.stream()
                .filter(intent -> intent.getStatus() == ExecutionStatus.FAILED_RETRYABLE)
                .collect(Collectors.toList());
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return The percentage of successful executions (0-100)
     */
    @JsonIgnore
    public double getSuccessRate() {
        if (executedIntents.isEmpty()) {
            return 0.0;
        }

        long successCount = executedIntents.stream()
                .filter(intent -> intent.getStatus() == ExecutionStatus.COMPLETED)
                .count();

        return (double) successCount / executedIntents.size() * 100.0;
    }

    /**
     * Gets a map of intent names to their execution status.
     *
     * @return Map of intent names to execution status
     */
    @JsonIgnore
    public Map<String, ExecutionStatus> getIntentStatusMap() {
        return executedIntents.stream()
                .collect(Collectors.toMap(
                        ExecutedIntent::getIntent,
                        ExecutedIntent::getStatus
                ));
    }

    /**
     * Adds a suggestion for remediation.
     *
     * @param suggestion The suggestion to add
     * @return This feedback instance for method chaining
     */
    public ExecutionFeedback addSuggestion(String suggestion) {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }
        suggestions.add(suggestion);
        return this;
    }

    /**
     * Sets the execution summary with performance metrics.
     *
     * @param summary The ExecutionSummary object
     * @return This feedback instance for method chaining
     */
    public ExecutionFeedback withSummary(ExecutionSummary summary) {
        if (summary != null) {
            this.executionSummary = String.format(
                    "Executed %d intents: %d completed, %d failed. Average duration: %s",
                    summary.getTotalIntents(),
                    summary.getCompleted(),
                    summary.getFailed(),
                    summary.getFormattedAverageDuration()
            );
        }
        return this;
    }

    /**
     * Generates default remediation suggestions based on the execution results.
     *
     * @return This feedback instance for method chaining
     */
    public ExecutionFeedback generateDefaultSuggestions() {
        if (suggestions == null) {
            suggestions = new ArrayList<>();
        }

        // Clear any existing suggestions
        suggestions.clear();

        // Generate new suggestions based on failed intents
        for (ExecutedIntent intent : getFailedIntents()) {
            String errorMsg = intent.getError();

            if (errorMsg != null) {
                if (errorMsg.contains("Missing required parameter")) {
                    suggestions.add(String.format(
                            "Provide the missing parameter for the '%s' intent.", intent.getIntent()
                    ));
                } else if (errorMsg.contains("not found")) {
                    suggestions.add(String.format(
                            "The tool '%s' does not exist. Please check the name and try again.",
                            intent.getIntent()
                    ));
                } else if (errorMsg.contains("timeout")) {
                    suggestions.add(String.format(
                            "The '%s' intent timed out. Try again later or with different parameters.",
                            intent.getIntent()
                    ));
                } else {
                    suggestions.add(String.format(
                            "Check the parameters for the '%s' intent and try again.",
                            intent.getIntent()
                    ));
                }
            }
        }

        // Add generic suggestions if needed
        if (!isComplete && suggestions.isEmpty()) {
            suggestions.add("Please try again with more specific parameters.");
        }

        return this;
    }
}