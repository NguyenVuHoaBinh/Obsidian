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
import java.util.HashMap;
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
    // Add these methods to the ExecutionFeedback class

    /**
     * Groups executed intents by name for better display and organization.
     *
     * @return A map of intent names to lists of executed intents
     */
    @JsonIgnore
    public Map<String, List<ExecutedIntent>> getIntentsByName() {
        return executedIntents.stream()
                .collect(Collectors.groupingBy(ExecutedIntent::getIntent));
    }

    /**
     * Generates a formatted summary of the execution, handling multiple instances
     * of the same intent with different parameters.
     *
     * @return A formatted summary string
     */
    @JsonIgnore
    public String getFormattedSummary() {
        StringBuilder builder = new StringBuilder();
        Map<String, List<ExecutedIntent>> intentsByName = getIntentsByName();

        builder.append("Execution Summary:\n");
        builder.append(String.format("- %d intents executed, %s complete\n",
                executedIntents.size(), isComplete ? "all" : "not all"));

        // Group by status for a quick overview
        long successCount = executedIntents.stream()
                .filter(ExecutedIntent::isSuccessful)
                .count();
        long failedCount = executedIntents.stream()
                .filter(ExecutedIntent::hasFailed)
                .count();

        builder.append(String.format("- %d succeeded, %d failed\n", successCount, failedCount));

        builder.append("\nDetailed Results:\n");
        for (Map.Entry<String, List<ExecutedIntent>> entry : intentsByName.entrySet()) {
            String intentName = entry.getKey();
            List<ExecutedIntent> instances = entry.getValue();

            builder.append(String.format("Intent: %s\n", intentName));

            if (instances.size() > 1) {
                builder.append(String.format("  Multiple executions (%d):\n", instances.size()));
                for (int i = 0; i < instances.size(); i++) {
                    ExecutedIntent instance = instances.get(i);
                    builder.append(String.format("  Instance %d: Status=%s\n",
                            i+1, instance.getStatus()));

                    // Format parameters
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = instance.getParameterMap();
                    if (params != null && !params.isEmpty()) {
                        builder.append("    Parameters: ");
                        boolean first = true;
                        for (Map.Entry<String, Object> param : params.entrySet()) {
                            if (!first) builder.append(", ");
                            first = false;
                            builder.append(param.getKey()).append("=");
                            if (param.getValue() instanceof String) {
                                builder.append("\"").append(param.getValue()).append("\"");
                            } else {
                                builder.append(param.getValue());
                            }
                        }
                        builder.append("\n");
                    }

                    // Add result or error info
                    if (instance.isSuccessful() && instance.getResult() != null) {
                        builder.append("    Result: ").append(formatResult(instance.getResult())).append("\n");
                    } else if (instance.hasFailed() && instance.getError() != null) {
                        builder.append("    Error: ").append(instance.getError()).append("\n");
                    }
                }
            } else if (instances.size() == 1) {
                ExecutedIntent instance = instances.get(0);
                builder.append(String.format("  Status: %s\n", instance.getStatus()));

                // Format parameters
                @SuppressWarnings("unchecked")
                Map<String, Object> params = instance.getParameterMap();
                if (params != null && !params.isEmpty()) {
                    builder.append("  Parameters: ");
                    boolean first = true;
                    for (Map.Entry<String, Object> param : params.entrySet()) {
                        if (!first) builder.append(", ");
                        first = false;
                        builder.append(param.getKey()).append("=");
                        if (param.getValue() instanceof String) {
                            builder.append("\"").append(param.getValue()).append("\"");
                        } else {
                            builder.append(param.getValue());
                        }
                    }
                    builder.append("\n");
                }

                // Add result or error info
                if (instance.isSuccessful() && instance.getResult() != null) {
                    builder.append("  Result: ").append(formatResult(instance.getResult())).append("\n");
                } else if (instance.hasFailed() && instance.getError() != null) {
                    builder.append("  Error: ").append(instance.getError()).append("\n");
                }
            }
        }

        // Add suggestions if available
        if (suggestions != null && !suggestions.isEmpty()) {
            builder.append("\nSuggestions:\n");
            for (String suggestion : suggestions) {
                builder.append("- ").append(suggestion).append("\n");
            }
        }

        return builder.toString();
    }

    /**
     * Formats a result object for display.
     *
     * @param result The result object to format
     * @return A formatted string representation of the result
     */
    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        } else if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            if (resultMap.isEmpty()) {
                return "{}";
            }

            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            int count = 0;
            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                if (count >= 3) {
                    sb.append(", ...");
                    break;
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(entry.getKey()).append("=");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value);
                }
                count++;
            }
            sb.append("}");
            return sb.toString();
        } else {
            return result.toString();
        }
    }

}