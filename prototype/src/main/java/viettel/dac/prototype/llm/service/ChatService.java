package viettel.dac.prototype.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.execution.service.ExecutionEngineService;
import viettel.dac.prototype.llm.exception.ChatProcessingException;
import viettel.dac.prototype.llm.model.ChatRequest;
import viettel.dac.prototype.llm.model.ChatResponse;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;
import viettel.dac.prototype.llm.repository.RedisConversationRepository;
import viettel.dac.prototype.llm.utils.ConversationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced service for processing chat messages with execution time tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmService llmService;
    private final RedisConversationRepository conversationRepository;
    private final ExecutionEngineService executionEngineService;
    private final ConversationUtils conversationUtils;
    private final ObjectMapper objectMapper;

    /**
     * Processes a chat message and returns a response with execution time tracking.
     *
     * @param request The chat request containing the user message
     * @param conversationId Optional conversation ID for existing conversations
     * @return A chat response containing the system's response
     */
    @Transactional(readOnly = true)
    public ChatResponse processMessage(ChatRequest request, String conversationId) {
        // Track overall processing time
        long startTime = System.currentTimeMillis();

        // Initialize detailed execution timings
        Map<String, Object> executionDetails = new LinkedHashMap<>();
        List<Map<String, Object>> toolExecutions = new ArrayList<>();

        try {
            log.info("Processing message for conversation: {}", conversationId != null ? conversationId : "new");

            // Record conversation retrieval time
            long conversationStart = System.currentTimeMillis();
            Conversation conversation = getOrCreateConversation(conversationId);
            long conversationTime = System.currentTimeMillis() - conversationStart;
            executionDetails.put("conversation_retrieval", conversationTime);

            // Add user message to conversation
            conversationUtils.addUserMessage(conversation, request.getMessage());

            // Analyze intent
            long intentStart = System.currentTimeMillis();
            IntentAnalysisResult intentAnalysis = llmService.analyzeIntent(
                    request.getMessage(), conversation);
            long intentTime = System.currentTimeMillis() - intentStart;
            executionDetails.put("intent_analysis", intentTime);

            // Convert to execution format
            AnalysisResult analysisResult = convertToAnalysisResult(intentAnalysis);

            // Record tools to be executed
            String toolsToExecute = analysisResult.getIntents() != null ?
                    analysisResult.getIntents().stream()
                            .map(Intent::getIntent)
                            .collect(Collectors.joining(", ")) :
                    "none";

            addRequestAttribute("X-Executed-Tools", toolsToExecute);

            // If no intents were detected, generate a direct response without tool execution
            if (analysisResult.getIntents() == null || analysisResult.getIntents().isEmpty()) {
                log.info("No intents detected, generating direct response");

                long directResponseStart = System.currentTimeMillis();
                String directResponse = llmService.generateDirectResponse(request.getMessage(), conversation);
                long directResponseTime = System.currentTimeMillis() - directResponseStart;

                executionDetails.put("direct_response", directResponseTime);
                addRequestAttribute("X-Detailed-Timings", objectMapper.writeValueAsString(executionDetails));

                conversationUtils.addSystemMessage(conversation, directResponse);

                ChatResponse response = new ChatResponse(directResponse, conversation.getId(), false);
                response.setExecutionDetails(new ArrayList<>()); // Empty since no tools executed

                return response;
            }

            // Execute tools
            log.info("Executing {} tools based on detected intents", analysisResult.getIntents().size());
            long executionStart = System.currentTimeMillis();
            ExecutionResult executionResult = executionEngineService.processAnalysis(analysisResult);
            long executionTime = System.currentTimeMillis() - executionStart;

            executionDetails.put("tool_execution", executionTime);

            // Record individual tool execution details
            if (executionResult.getExecutionRecords() != null) {
                executionResult.getExecutionRecords().forEach(record -> {
                    Map<String, Object> toolExecution = new HashMap<>();
                    toolExecution.put("tool", record.getIntent());
                    toolExecution.put("status", record.getStatus().toString());
                    toolExecution.put("executionTime", record.getDurationMillis());
                    toolExecutions.add(toolExecution);
                });
            }

            log.info("Execution complete: {}/{} tools executed successfully",
                    executionResult.getSummary().getCompleted(),
                    executionResult.getSummary().getTotalIntents());

            // Generate feedback for LLM
            long feedbackStart = System.currentTimeMillis();
            ExecutionFeedback feedback = executionEngineService.generateFeedback(executionResult);
            long feedbackTime = System.currentTimeMillis() - feedbackStart;
            executionDetails.put("feedback_generation", feedbackTime);

            // Add execution result to conversation context
            conversationUtils.addExecutionResult(conversation, executionResult);

            // Generate response based on execution results
            long responseStart = System.currentTimeMillis();
            String systemResponse = llmService.generateResponseFromFeedback(conversation, feedback);
            long responseTime = System.currentTimeMillis() - responseStart;
            executionDetails.put("response_generation", responseTime);

            // Add system response to conversation
            conversationUtils.addSystemMessage(conversation, systemResponse);

            // Calculate total time
            long totalTime = System.currentTimeMillis() - startTime;
            executionDetails.put("total_time", totalTime);

            // Add timing information to request for filter to use
            addRequestAttribute("X-Detailed-Timings", objectMapper.writeValueAsString(executionDetails));

            // Create response
            boolean requiresFollowUp = executionResult.getSummary().getFailed() > 0;
            ChatResponse response = new ChatResponse(systemResponse, conversation.getId(), requiresFollowUp);
            response.setExecutionDetails(toolExecutions);

            return response;
        } catch (Exception e) {
            log.error("Error processing chat message", e);
            // Record error information
            executionDetails.put("error", e.getMessage());
            try {
                addRequestAttribute("X-Detailed-Timings", objectMapper.writeValueAsString(executionDetails));
            } catch (Exception jsonException) {
                log.error("Error serializing execution details", jsonException);
            }
            throw new ChatProcessingException("Failed to process chat message", e);
        }
    }

    /**
     * Helper method to add attributes to the current request.
     * These will be picked up by the ExecutionTimeFilter.
     */
    private void addRequestAttribute(String name, String value) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                request.setAttribute(name, value);
            }
        } catch (Exception e) {
            log.warn("Unable to add request attribute {}: {}", name, e.getMessage());
        }
    }

    /**
     * Gets an existing conversation or creates a new one.
     */
    private Conversation getOrCreateConversation(String conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseGet(() -> {
                        log.info("Conversation not found, creating new one");
                        return conversationRepository.createNew();
                    });
        } else {
            return conversationRepository.createNew();
        }
    }

    /**
     * Converts internal intent analysis to execution engine format.
     */
    private AnalysisResult convertToAnalysisResult(IntentAnalysisResult intentAnalysis) {
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisId(intentAnalysis.getAnalysisId());
        result.setIntents(intentAnalysis.getIntents());
        result.setConfidence(intentAnalysis.getConfidence());
        result.setMultiIntent(intentAnalysis.isMultiIntent());
        return result;
    }
}