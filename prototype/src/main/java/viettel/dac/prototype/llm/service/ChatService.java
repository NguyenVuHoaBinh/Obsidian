package viettel.dac.prototype.llm.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.model.AnalysisResult;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.service.ExecutionEngineService;
import viettel.dac.prototype.llm.model.ChatRequest;
import viettel.dac.prototype.llm.model.ChatResponse;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;
import viettel.dac.prototype.llm.repository.ConversationRepository;


/**
 * Service responsible for processing chat messages and managing the conversation flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmService llmService;
    private final ConversationRepository conversationRepository;
    private final ExecutionEngineService executionEngineService;

    /**
     * Processes a chat message and returns a response.
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
            conversation.addUserMessage(request.getMessage());

            // Analyze intent
            IntentAnalysisResult intentAnalysis = llmService.analyzeIntent(
                    request.getMessage(), conversation);

            // Convert to execution format
            AnalysisResult analysisResult = convertToAnalysisResult(intentAnalysis);

            // Execute tools
            ExecutionResult executionResult = executionEngineService.processAnalysis(analysisResult);
            log.info("Execution complete: {}/{} tools executed successfully",
                    executionResult.getSummary().getCompleted(),
                    executionResult.getSummary().getTotalIntents());

            // Add execution result to conversation
            conversation.addExecutionResult(executionResult);

            // Generate response
            String systemResponse = llmService.generateResponse(conversation, executionResult);

            // Add system response to conversation
            conversation.addSystemMessage(systemResponse);

            // Save conversation
            conversationRepository.save(conversation);

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
