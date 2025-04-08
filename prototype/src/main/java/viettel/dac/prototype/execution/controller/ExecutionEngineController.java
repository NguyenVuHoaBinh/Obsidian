package viettel.dac.prototype.execution.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.execution.exception.InvalidRequestException;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.service.ExecutionEngineService;
import viettel.dac.prototype.execution.utils.ExecutionUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * REST controller for the execution engine.
 * Handles requests to execute tools based on analysis results.
 */
@RestController
@RequestMapping("/api/execute")
@Tag(name = "Execution Engine", description = "APIs for executing tools based on analysis results")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RequiredArgsConstructor
public class ExecutionEngineController {

    private final ExecutionEngineService executionService;
    private final LLMResponseService llmResponseService;

    /**
     * Executes tools based on analysis results and generates a user-friendly response.
     *
     * @param analysisId     The ID of the analysis to execute.
     * @param analysisResult The analysis result containing intents and parameters.
     * @return A user-friendly response based on the execution results.
     * @throws InvalidRequestException If the analysis ID doesn't match the one in the request body.
     */
    @PostMapping("/{analysisId}")
    @Operation(
            summary = "Execute tools based on analysis results",
            description = "Processes an analysis result, executes the identified tools in the correct order, and generates a user-friendly response"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful execution",
                    content = @Content(schema = @Schema(implementation = String.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
            @ApiResponse(responseCode = "500", description = "Execution failure")
    })
    @RateLimiter(name = "execution")
    @Timed(value = "execution.api.response.time", description = "Time taken to execute tools and generate response")
    @PreAuthorize("hasRole('TOOL_EXECUTOR')")
    public ResponseEntity<String> executeAndRespond(
            @Parameter(description = "ID of the analysis to execute", required = true)
            @PathVariable String analysisId,

            @Parameter(description = "Analysis result containing intents and parameters", required = true)
            @Valid @RequestBody AnalysisResult analysisResult) {

        log.info("Received execution request for analysis: {}", analysisId);
        LocalDateTime startTime = LocalDateTime.now();

        // Validate analysis ID match
        if (!analysisResult.getAnalysisId().equals(analysisId)) {
            log.warn("Analysis ID mismatch: expected {}, got {}",
                    analysisId, analysisResult.getAnalysisId());
            throw new InvalidRequestException("Analysis ID mismatch: expected " +
                    analysisId + ", got " + analysisResult.getAnalysisId());
        }

        // Execute tools and generate feedback
        try {
            log.debug("Starting execution for analysis {}", analysisId);
            ExecutionResult result = executionService.processAnalysis(analysisResult);
            ExecutionFeedback feedback = executionService.generateFeedback(result);

            log.debug("Generating user response for analysis {}", analysisId);
            String userResponse = llmResponseService.generateUserResponse(feedback);

            Duration processingTime = Duration.between(startTime, LocalDateTime.now());
            log.info("Execution completed for analysis {} in {}",
                    analysisId, ExecutionUtils.formatDurationDetailed(processingTime.toMillis()));

            return ResponseEntity.ok()
                    .header("X-Processing-Time", processingTime.toString())
                    .header("X-Execution-Status", result.getSummary().getFailed() > 0 ? "PARTIAL" : "COMPLETE")
                    .body(userResponse);
        } catch (Exception e) {
            log.error("Error executing analysis {}", analysisId, e);
            throw e;
        }
    }

    /**
     * Provides detailed execution results for a specific analysis.
     * This endpoint is primarily for debugging and monitoring purposes.
     *
     * @param analysisId     The ID of the analysis to fetch results for.
     * @param analysisResult The analysis result containing intents and parameters.
     * @return Detailed execution results.
     */
    @PostMapping("/{analysisId}/details")
    @Operation(
            summary = "Get detailed execution results",
            description = "Returns detailed execution results for a specific analysis"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful execution",
                    content = @Content(schema = @Schema(implementation = ExecutionResult.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Tool not found"),
            @ApiResponse(responseCode = "500", description = "Execution failure")
    })
    @PreAuthorize("hasRole('TOOL_ADMIN')")
    public ResponseEntity<ExecutionResult> getExecutionDetails(
            @PathVariable String analysisId,
            @Valid @RequestBody AnalysisResult analysisResult) {

        log.info("Received request for detailed execution results for analysis: {}", analysisId);

        // Validate analysis ID match
        if (!analysisResult.getAnalysisId().equals(analysisId)) {
            throw new InvalidRequestException("Analysis ID mismatch");
        }

        // Execute tools and return the detailed result
        ExecutionResult result = executionService.processAnalysis(analysisResult);

        return ResponseEntity.ok(result);
    }
}