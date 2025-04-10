package viettel.dac.prototype.execution.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import viettel.dac.prototype.execution.enums.ExecutionState;
import viettel.dac.prototype.execution.enums.ExecutionStatus;
import viettel.dac.prototype.execution.exception.ExecutionFailureException;
import viettel.dac.prototype.execution.exception.MissingDependencyException;
import viettel.dac.prototype.execution.exception.MissingParameterException;
import viettel.dac.prototype.execution.exception.ToolNotFoundException;
import viettel.dac.prototype.execution.exception.UnsupportedHttpMethodException;
import viettel.dac.prototype.execution.model.*;
import viettel.dac.prototype.execution.utils.DependencyResolver;
import viettel.dac.prototype.execution.utils.ExecutionUtils;
import viettel.dac.prototype.tool.model.Parameter;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service responsible for processing analysis results, executing tools in the correct order,
 * and generating feedback.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionEngineService {

    private final ToolRepository toolRepository;
    private final DependencyResolver dependencyResolver;
    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    /**
     * Processes the analysis result from the LLM and executes the tools in the correct order.
     *
     * @param analysisResult The analysis result containing intents and parameters.
     * @return The execution result with details of each tool execution.
     */
    @Transactional(readOnly = true)
    public ExecutionResult processAnalysis(AnalysisResult analysisResult) {
        log.info("Processing analysis: {}", analysisResult.getAnalysisId());
        Timer.Sample timer = Timer.start(meterRegistry);

        ExecutionResult result = new ExecutionResult();
        result.setAnalysisId(analysisResult.getAnalysisId());
        result.setExecutionTime(LocalDateTime.now());

        try {
            // Resolve execution order based on dependencies
            List<Intent> orderedIntents;
            try {
                orderedIntents = dependencyResolver.resolveExecutionOrder(
                        analysisResult.getIntents()
                );
                log.debug("Resolved execution order: {}",
                        orderedIntents.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));
            } catch (MissingDependencyException e) {
                log.error("Missing dependencies detected: {}", e.getMessage());

                // Create execution record for each intent with failed status and error message
                for (Intent intent : analysisResult.getIntents()) {
                    ExecutionRecord record = new ExecutionRecord();
                    record.setIntent(intent.getIntent());
                    record.setParameters(intent.getParameters());
                    record.setStartTime(LocalDateTime.now());
                    record.setEndTime(LocalDateTime.now());
                    record.setStatus(ExecutionStatus.FAILED_PERMANENT);

                    // Check if this intent has missing dependencies
                    if (e.getMissingDependencies().containsKey(intent.getIntent())) {
                        List<String> missingDeps = e.getMissingDependencies().get(intent.getIntent());
                        StringBuilder errorMessage = new StringBuilder("Missing required dependencies: ");

                        for (int i = 0; i < missingDeps.size(); i++) {
                            String depName = missingDeps.get(i);
                            MissingDependencyException.ToolMetadata metadata = e.getMissingToolsMetadata().get(depName);

                            if (i > 0) {
                                errorMessage.append(", ");
                            }

                            errorMessage.append(depName);

                            if (metadata != null) {
                                errorMessage.append(" (").append(metadata.description()).append(")");

                                // Add parameter information
                                if (!metadata.parameters().isEmpty()) {
                                    errorMessage.append("\nParameters for ").append(depName).append(":");
                                    for (MissingDependencyException.ParameterInfo param : metadata.parameters()) {
                                        errorMessage.append("\n  - ").append(param.name())
                                                .append(" (").append(param.type()).append(")");
                                        if (param.required()) {
                                            errorMessage.append(" [REQUIRED]");
                                        }
                                        errorMessage.append(": ").append(param.description());

                                        if (param.defaultValue() != null && !param.defaultValue().isEmpty()) {
                                            errorMessage.append(" (Default: ").append(param.defaultValue()).append(")");
                                        }
                                    }
                                }

                                // Add dependency information if any
                                if (!metadata.dependencies().isEmpty()) {
                                    errorMessage.append("\nDependencies for ").append(depName).append(": ")
                                            .append(String.join(", ", metadata.dependencies()));
                                }
                            }
                        }

                        record.setError(errorMessage.toString());
                    } else {
                        record.setError("Execution skipped due to missing dependencies in other tools");
                    }

                    result.getExecutionRecords().add(record);
                    intent.setState(ExecutionState.FAILED);
                }

                // Generate summary statistics
                result.setSummary(generateSummary(result.getExecutionRecords()));

                log.info("Analysis processing failed due to missing dependencies. " +
                                "Total intents: {}, Failed: {}",
                        result.getSummary().getTotalIntents(),
                        result.getSummary().getFailed());

                return result;
            }

            // Execute tools in resolved order
            orderedIntents.forEach(intent -> {
                ExecutionRecord record = processIntent(intent);
                result.getExecutionRecords().add(record);

                // Record intent-specific metrics
                meterRegistry.counter("execution.intent.count",
                        "intent", intent.getIntent(),
                        "status", record.getStatus().toString()).increment();
                meterRegistry.timer("execution.intent.duration",
                        "intent", intent.getIntent()).record(record.getDurationMillis(), TimeUnit.MILLISECONDS);
            });

            // Generate summary statistics
            result.setSummary(generateSummary(result.getExecutionRecords()));

            log.info("Analysis processing completed. Total intents: {}, Completed: {}, Failed: {}",
                    result.getSummary().getTotalIntents(),
                    result.getSummary().getCompleted(),
                    result.getSummary().getFailed());

            return result;
        } finally {
            timer.stop(Timer.builder("execution.analysis.duration")
                    .tag("analysis_id", analysisResult.getAnalysisId())
                    .tag("has_errors", Boolean.toString(result.getSummary() != null && result.getSummary().isHasErrors()))
                    .register(meterRegistry));
        }
    }

    /**
     * Generates feedback for the LLM based on execution results.
     *
     * @param result The execution result containing records of tool executions.
     * @return Feedback for the LLM with details of successes, failures, and suggestions.
     */
    public ExecutionFeedback generateFeedback(ExecutionResult result) {
        log.debug("Generating feedback for analysis: {}", result.getAnalysisId());

        ExecutionFeedback feedback = new ExecutionFeedback();
        feedback.setAnalysisId(result.getAnalysisId());
        feedback.setComplete(result.getSummary().getFailed() == 0);

        List<ExecutedIntent> executedIntents = result.getExecutionRecords().stream()
                .map(record -> new ExecutedIntent(
                        record.getIntent(),
                        record.getStatus(),
                        record.getResult(),
                        record.getError()
                ))
                .collect(Collectors.toList());

        feedback.setExecutedIntents(executedIntents);

        String errorSummary = result.getExecutionRecords().stream()
                .filter(r -> r.getStatus() == ExecutionStatus.FAILED_RETRYABLE ||
                        r.getStatus() == ExecutionStatus.FAILED_PERMANENT)
                .map(r -> "Intent '" + r.getIntent() + "' failed: " + r.getError())
                .collect(Collectors.joining("\n"));

        feedback.setErrorSummary(errorSummary);

        log.debug("Feedback generated: complete={}, errors={}",
                feedback.isComplete(),
                !errorSummary.isEmpty() ? "yes" : "no");

        // Generate default suggestions
        feedback.generateDefaultSuggestions();

        // Add execution summary
        feedback.withSummary(result.getSummary());

        return feedback;
    }

    /**
     * Processes an individual intent by executing the corresponding tool.
     *
     * @param intent The intent to process.
     * @return The execution record with details of the tool execution.
     */
    private ExecutionRecord processIntent(Intent intent) {
        log.info("Processing intent: {}", intent.getIntent());
        Timer.Sample intentTimer = Timer.start(meterRegistry);

        ExecutionRecord record = new ExecutionRecord();
        record.setIntent(intent.getIntent());
        record.setParameters(intent.getParameters());
        record.setStartTime(LocalDateTime.now());
        record.setStatus(ExecutionStatus.IN_PROGRESS);

        try {
            Tool tool = toolRepository.findByName(intent.getIntent())
                    .orElseThrow(() -> {
                        log.error("Tool not found: {}", intent.getIntent());
                        return new ToolNotFoundException(intent.getIntent());
                    });

            validateParameters(tool, intent.getParameters());
            log.debug("Parameters validated for tool: {}", tool.getName());

            // Execute with timeout
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeTool(tool, intent.getParameters());
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });

            Map<String, Object> executionResult;
            try {
                executionResult = future.get(tool.getTimeoutMs(), TimeUnit.MILLISECONDS);
                record.setStatus(ExecutionStatus.COMPLETED);
                record.setResult(executionResult);
                intent.setState(ExecutionState.COMPLETED);
                log.info("Intent {} executed successfully", intent.getIntent());
            } catch (TimeoutException e) {
                future.cancel(true);
                String errorMsg = "Execution timed out after " + tool.getTimeoutMs() + "ms";
                log.warn("Intent {} timed out: {}", intent.getIntent(), errorMsg);
                record.setStatus(ExecutionStatus.FAILED_RETRYABLE);
                record.setError(errorMsg);
                intent.setState(ExecutionState.FAILED);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                handleExecutionException(record, intent, cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Intent {} execution interrupted", intent.getIntent(), e);
                record.setStatus(ExecutionStatus.FAILED_RETRYABLE);
                record.setError("Execution interrupted: " + e.getMessage());
                intent.setState(ExecutionState.FAILED);
            }
        } catch (Exception e) {
            handleExecutionException(record, intent, e);
        } finally {
            record.setEndTime(LocalDateTime.now());
            record.setDurationMillis(ExecutionUtils.calculateDurationMillis(
                    record.getStartTime(),
                    record.getEndTime()
            ));

            intentTimer.stop(Timer.builder("execution.intent.processing.duration")
                    .tag("intent", intent.getIntent())
                    .tag("status", record.getStatus().toString())
                    .register(meterRegistry));

            log.debug("Intent {} processed in {}ms with status: {}",
                    intent.getIntent(),
                    record.getDurationMillis(),
                    record.getStatus());
        }

        return record;
    }

    /**
     * Handles exceptions that occur during tool execution and updates the record and intent accordingly.
     *
     * @param record The execution record to update.
     * @param intent The intent being processed.
     * @param exception The exception that occurred.
     */
    private void handleExecutionException(ExecutionRecord record, Intent intent, Throwable exception) {
        if (exception instanceof MissingParameterException) {
            log.error("Missing parameter for intent {}: {}", intent.getIntent(), exception.getMessage());
            record.setStatus(ExecutionStatus.FAILED_PERMANENT);
        } else if (exception instanceof HttpClientErrorException) {
            log.error("Client error for intent {}: {}", intent.getIntent(), exception.getMessage());
            record.setStatus(ExecutionStatus.FAILED_PERMANENT);
        } else if (exception instanceof HttpServerErrorException ||
                exception instanceof WebClientResponseException.InternalServerError) {
            log.warn("Server error for intent {}: {}", intent.getIntent(), exception.getMessage());
            record.setStatus(ExecutionStatus.FAILED_RETRYABLE);
        } else {
            log.error("Error executing intent {}", intent.getIntent(), exception);
            record.setStatus(ExecutionStatus.FAILED_RETRYABLE);
        }

        record.setError(exception.getMessage());
        intent.setState(ExecutionState.FAILED);
    }

    /**
     * Validates that all required parameters are present for a tool's execution.
     *
     * @param tool The tool to validate parameters for.
     * @param parameters The parameters provided for execution.
     * @throws MissingParameterException If a required parameter is missing.
     */
    private void validateParameters(Tool tool, Map<String, Object> parameters) {
        for (Parameter param : tool.getParameters()) {
            if (param.isRequired() && (!parameters.containsKey(param.getName()) || parameters.get(param.getName()) == null)) {
                throw new MissingParameterException(param.getName());
            }
        }
    }

    /**
     * Executes a tool by making an HTTP request to its endpoint.
     * Includes retry capabilities for transient errors.
     *
     * @param tool The tool to execute.
     * @param params The parameters to send to the tool.
     * @return The response from the tool.
     * @throws UnsupportedHttpMethodException If the HTTP method is not supported.
     * @throws ExecutionFailureException If the tool execution fails.
     */
    @Retryable(
            retryFor = {HttpServerErrorException.class, WebClientResponseException.InternalServerError.class},
            noRetryFor = {IllegalArgumentException.class},
            notRecoverable = {SecurityException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    private Map<String, Object> executeTool(Tool tool, Map<String, Object> params) {
        log.debug("Executing tool: {} with HTTP method: {}", tool.getName(), tool.getHttpMethod());

        try {
            return switch (tool.getHttpMethod()) {
                case GET -> webClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path(tool.getEndpoint());
                            params.forEach(uriBuilder::queryParam);
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofMillis(tool.getTimeoutMs()))
                        .block();
                case POST -> webClient.post()
                        .uri(tool.getEndpoint())
                        .bodyValue(params)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofMillis(tool.getTimeoutMs()))
                        .block();
                case PUT -> {
                    webClient.put()
                            .uri(tool.getEndpoint())
                            .bodyValue(params)
                            .retrieve()
                            .bodyToMono(Void.class)
                            .timeout(Duration.ofMillis(tool.getTimeoutMs()))
                            .block();
                    yield Map.of("status", "success");
                }
                case DELETE -> {
                    webClient.delete()
                            .uri(uriBuilder -> {
                                uriBuilder.path(tool.getEndpoint());
                                params.forEach(uriBuilder::queryParam);
                                return uriBuilder.build();
                            })
                            .retrieve()
                            .bodyToMono(Void.class)
                            .timeout(Duration.ofMillis(tool.getTimeoutMs()))
                            .block();
                    yield Map.of("status", "success");
                }
                case PATCH, HEAD, OPTIONS -> throw new UnsupportedHttpMethodException(tool.getHttpMethod().name());
            };
        } catch (WebClientResponseException e) {
            log.error("WebClient error when executing tool {}: {} - {}",
                    tool.getName(), e.getStatusCode(), e.getMessage());

            if (e.getStatusCode().is4xxClientError()) {
                throw new ExecutionFailureException("Client error: " + e.getMessage(), e);
            } else if (e.getStatusCode().is5xxServerError()) {
                throw new ExecutionFailureException("Server error: " + e.getMessage(), e);
            } else {
                throw new ExecutionFailureException("HTTP error: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Unexpected error when executing tool {}", tool.getName(), e);
            throw new ExecutionFailureException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Recovery method for the retryable executeTool method.
     * Called after all retry attempts have been exhausted.
     *
     * @param e The exception that caused the retry to fail.
     * @param tool The tool that was being executed.
     * @param params The parameters that were passed to the tool.
     * @return A fallback response or throws a permanent failure exception.
     */
    @Recover
    private Map<String, Object> recoverFromHttpError(Exception e, Tool tool, Map<String, Object> params) {
        log.error("All retry attempts failed for tool {}", tool.getName(), e);
        throw new ExecutionFailureException("All retry attempts failed: " + e.getMessage(), e);
    }

    /**
     * Generates summary statistics from a list of execution records.
     *
     * @param records The execution records to summarize.
     * @return A summary of the execution records.
     */
    private ExecutionSummary generateSummary(List<ExecutionRecord> records) {
        ExecutionSummary summary = new ExecutionSummary();
        summary.setTotalIntents(records.size());

        long totalDuration = 0;
        long fastestExecution = Long.MAX_VALUE;
        long slowestExecution = 0;

        int completed = 0;
        int failed = 0;

        for (ExecutionRecord record : records) {
            if (record.getStatus() == ExecutionStatus.COMPLETED) {
                completed++;
                long duration = record.getDurationMillis();
                totalDuration += duration;

                if (duration < fastestExecution) {
                    fastestExecution = duration;
                }
                if (duration > slowestExecution) {
                    slowestExecution = duration;
                }
            } else if (record.getStatus() == ExecutionStatus.FAILED_PERMANENT ||
                    record.getStatus() == ExecutionStatus.FAILED_RETRYABLE) {
                failed++;
            }
        }

        summary.setCompleted(completed);
        summary.setFailed(failed);
        summary.setAverageDuration(completed > 0 ? (double) totalDuration / completed : 0);
        summary.setFastestExecution(completed > 0 ? fastestExecution : 0);
        summary.setSlowestExecution(slowestExecution);
        summary.setTotalDuration(totalDuration);
        summary.setHasErrors(failed > 0);

        return summary;
    }
    /**
     * Formats execution results for logs and reporting, handling multiple instances of the same intent.
     */
    private String formatExecutionResults(List<ExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return "No intents executed.";
        }

        // Group records by intent name
        Map<String, List<ExecutionRecord>> recordsByIntent = records.stream()
                .collect(Collectors.groupingBy(ExecutionRecord::getIntent));

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, List<ExecutionRecord>> entry : recordsByIntent.entrySet()) {
            String intentName = entry.getKey();
            List<ExecutionRecord> intentRecords = entry.getValue();

            sb.append("Intent '").append(intentName).append("': ");

            if (intentRecords.size() > 1) {
                sb.append("Multiple executions (").append(intentRecords.size()).append("):\n");

                for (int i = 0; i < intentRecords.size(); i++) {
                    ExecutionRecord record = intentRecords.get(i);
                    sb.append("  - Execution ").append(i + 1)
                            .append(" (").append(record.getDisplayName()).append("): ")
                            .append(record.getStatus())
                            .append(" - Parameters: ").append(formatParameters(record.getParameters()));

                    if (record.getStatus() == ExecutionStatus.COMPLETED) {
                        sb.append(" - Result: ").append(formatResult(record.getResult()));
                    } else if (record.hasFailed()) {
                        sb.append(" - Error: ").append(record.getError());
                    }

                    sb.append("\n");
                }
            } else if (intentRecords.size() == 1) {
                ExecutionRecord record = intentRecords.get(0);
                sb.append(record.getStatus())
                        .append(" - Parameters: ").append(formatParameters(record.getParameters()));

                if (record.getStatus() == ExecutionStatus.COMPLETED) {
                    sb.append(" - Result: ").append(formatResult(record.getResult()));
                } else if (record.hasFailed()) {
                    sb.append(" - Error: ").append(record.getError());
                }

                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Formats parameters map for display
     */
    private String formatParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "{}";
        }

        return parameters.entrySet().stream()
                .map(e -> e.getKey() + "=" + formatValue(e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Formats a parameter value for display
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else {
            return value.toString();
        }
    }

    /**
     * Formats result map for display
     */
    private String formatResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "{}";
        }

        return result.entrySet().stream()
                .limit(3) // Limit to first 3 entries to avoid very long strings
                .map(e -> e.getKey() + "=" + formatValue(e.getValue()))
                .collect(Collectors.joining(", ", "{", result.size() > 3 ? ", ...}" : "}"));
    }
}