package viettel.dac.prototype.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.service.ExecutionEngineService;
import viettel.dac.prototype.llm.exception.ChatProcessingException;
import viettel.dac.prototype.llm.model.ChatRequest;
import viettel.dac.prototype.llm.model.ChatResponse;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;
import viettel.dac.prototype.llm.repository.RedisConversationRepository;
import viettel.dac.prototype.llm.utils.ConversationUtils;

/**
 * Optimized service for processing chat messages using the improved Redis storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService{

    private final LlmService llmService;
    private final RedisConversationRepository conversationRepository;
    private final ExecutionEngineService executionEngineService;
    private final ConversationUtils conversationUtils;

    /**
     * Processes a chat message and returns a response.
     * Uses the optimized Redis repository for more efficient conversation management.
     *
     * @param request The chat request containing the user message
     * @param conversationId Optional conversation ID for existing conversations
     * @return A chat response containing the system's response
     */
    public ChatResponse processMessage(ChatRequest request, String conversationId) {
        try {
            log.info("Processing message for conversation: {}", conversationId != null ? conversationId : "new");

            // Get or create conversation
            Conversation conversation = getOrCreateConversation(conversationId);

            // Add user message to conversation
            conversationUtils.addUserMessage(conversation, request.getMessage());

            // Analyze intent
            IntentAnalysisResult intentAnalysis = llmService.analyzeIntent(
                    request.getMessage(), conversation);

            // Convert to execution format
            AnalysisResult analysisResult = convertToAnalysisResult(intentAnalysis);

            // If no intents were detected, generate a direct response without tool execution
            if (analysisResult.getIntents() == null || analysisResult.getIntents().isEmpty()) {
                log.info("No intents detected, generating direct response");
                String directResponse = llmService.generateDirectResponse(request.getMessage(), conversation);
                conversationUtils.addSystemMessage(conversation, directResponse);

                // No need to explicitly save the conversation as the utils handle it

                return new ChatResponse(directResponse, conversation.getId(), false);
            }

            // Execute tools
            log.info("Executing {} tools based on detected intents", analysisResult.getIntents().size());
            ExecutionResult executionResult = executionEngineService.processAnalysis(analysisResult);
            log.info("Execution complete: {}/{} tools executed successfully",
                    executionResult.getSummary().getCompleted(),
                    executionResult.getSummary().getTotalIntents());

            // Generate feedback for LLM
            ExecutionFeedback feedback = executionEngineService.generateFeedback(executionResult);

            // Add execution result to conversation context
            conversationUtils.addExecutionResult(conversation, executionResult);

            // Generate response based on execution results
            String systemResponse = llmService.generateResponseFromFeedback(conversation, feedback);

            // Add system response to conversation
            conversationUtils.addSystemMessage(conversation, systemResponse);

            // Create response
            boolean requiresFollowUp = executionResult.getSummary().getFailed() > 0;
            return new ChatResponse(systemResponse, conversation.getId(), requiresFollowUp);

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            throw new ChatProcessingException("Failed to process chat message", e);
        }
    }

    /**
     * Gets an existing conversation or creates a new one.
     * Uses the optimized repository implementation.
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