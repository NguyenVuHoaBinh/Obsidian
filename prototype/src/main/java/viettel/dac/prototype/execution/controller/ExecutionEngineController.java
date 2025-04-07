package viettel.dac.prototype.execution.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.execution.exception.InvalidRequestException;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.service.ExecutionEngineService;
import viettel.dac.prototype.llm.service.LLMResponseService;

@RestController
@RequestMapping("/api/execute")
public class ExecutionEngineController {

    @Autowired
    private ExecutionEngineService executionService;

    @Autowired
    private LLMResponseService llmResponseService;

    @PostMapping("/{analysisId}")
    public ResponseEntity<String> executeAndRespond(
            @PathVariable String analysisId,
            @RequestBody AnalysisResult analysisResult) {

        // Validate analysis ID match
        if (!analysisResult.getAnalysisId().equals(analysisId)) {
            throw new InvalidRequestException("Analysis ID mismatch");
        }

        // Execute tools and generate feedback
        ExecutionResult result = executionService.processAnalysis(analysisResult);
        ExecutionFeedback feedback = executionService.generateFeedback(result);

        // Generate user-facing response using LLM module
        String userResponse = llmResponseService.generateUserResponse(feedback);

        return ResponseEntity.ok(userResponse);
    }
}
