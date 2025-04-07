package viettel.dac.prototype.execution.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import viettel.dac.prototype.execution.enums.ExecutionState;
import viettel.dac.prototype.execution.enums.ExecutionStatus;
import viettel.dac.prototype.execution.exception.MissingParameterException;
import viettel.dac.prototype.execution.exception.ToolNotFoundException;
import viettel.dac.prototype.execution.exception.UnsupportedHttpMethodException;
import viettel.dac.prototype.execution.model.*;
import viettel.dac.prototype.execution.utils.DependencyResolver;
import viettel.dac.prototype.execution.utils.ExecutionUtils;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExecutionEngineService {

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private DependencyResolver dependencyResolver;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Processes the analysis result from the LLM and executes the tools in the correct order.
     *
     * @param analysisResult The analysis result containing intents and parameters.
     * @return The execution result with details of each tool execution.
     */
    public ExecutionResult processAnalysis(AnalysisResult analysisResult) {
        ExecutionResult result = new ExecutionResult();
        result.setAnalysisId(analysisResult.getAnalysisId());

        // Resolve execution order based on dependencies
        List<Intent> orderedIntents = dependencyResolver.resolveExecutionOrder(
                analysisResult.getIntents()
        );

        // Execute tools in resolved order
        orderedIntents.forEach(intent -> {
            ExecutionRecord record = processIntent(intent);
            result.getExecutionRecords().add(record);
        });

        // Generate summary statistics
        result.setSummary(generateSummary(result.getExecutionRecords()));
        return result;
    }

    /**
     * Generates feedback for the LLM based on execution results.
     *
     * @param result The execution result containing records of tool executions.
     * @return Feedback for the LLM with details of successes, failures, and suggestions.
     */
    public ExecutionFeedback generateFeedback(ExecutionResult result) {
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

        return feedback;
    }


    private ExecutionRecord processIntent(Intent intent) {
        ExecutionRecord record = new ExecutionRecord();
        record.setIntent(intent.getIntent());
        record.setParameters(intent.getParameters());
        record.setStartTime(LocalDateTime.now());

        try {
            Tool tool = toolRepository.findByName(intent.getIntent())
                    .orElseThrow(() -> new ToolNotFoundException(intent.getIntent()));

            validateParameters(tool, intent.getParameters());

            Map<String, Object> executionResult = executeTool(tool, intent.getParameters());

            record.setStatus(ExecutionStatus.COMPLETED);
            record.setResult(executionResult);
            intent.setState(ExecutionState.COMPLETED);

        } catch (Exception e) {
            record.setStatus(e instanceof MissingParameterException ?
                    ExecutionStatus.FAILED_PERMANENT :
                    ExecutionStatus.FAILED_RETRYABLE);
            record.setError(e.getMessage());
            intent.setState(ExecutionState.FAILED);
        }

        record.setEndTime(LocalDateTime.now());
        record.setDurationMillis(ExecutionUtils.calculateDurationMillis(
                record.getStartTime(),
                record.getEndTime()
        ));

        return record;
    }

    private void validateParameters(Tool tool, Map<String, Object> parameters) {
        tool.getParameters().forEach(param -> {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                throw new MissingParameterException(param.getName());
            }
        });
    }

    private Map<String, Object> executeTool(Tool tool, Map<String, Object> params) {
        return switch (tool.getHttpMethod().toUpperCase()) {
            case "GET" -> restTemplate.getForObject(tool.getEndpoint(), Map.class, params);
            case "POST" -> restTemplate.postForObject(tool.getEndpoint(), params, Map.class);
            case "PUT" -> {
                restTemplate.put(tool.getEndpoint(), params);
                yield Map.of("status", "success");
            }
            case "DELETE" -> {
                restTemplate.delete(tool.getEndpoint(), params);
                yield Map.of("status", "success");
            }
            default -> throw new UnsupportedHttpMethodException(tool.getHttpMethod());
        };
    }

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
