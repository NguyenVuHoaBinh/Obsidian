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

        try {
            // Resolve execution order based on dependencies
            List<Intent> orderedIntents = dependencyResolver.resolveExecutionOrder(
                    analysisResult.getIntents()
            );
            log.debug("Resolved execution order: {}",
                    orderedIntents.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));

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

        long totalDuration = records.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.COMPLETED)
                .mapToLong(ExecutionRecord::getDurationMillis)
                .sum();

        summary.setCompleted((int) records.stream()
                .filter(r -> r.getStatus() == ExecutionStatus.COMPLETED)
                .count());

        summary.setFailed(summary.getTotalIntents() - summary.getCompleted());
        summary.setAverageDuration(summary.getCompleted() > 0 ?
                totalDuration / summary.getCompleted() : 0);
        summary.setHasErrors(summary.getFailed() > 0);

        return summary;
    }
}