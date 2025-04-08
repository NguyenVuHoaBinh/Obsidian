package viettel.dac.prototype.llm.service;

import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;

/**
 * Service interface for LLM operations.
 */
public interface LlmService {

    /**
     * Analyzes the user's message to determine intents and parameters.
     *
     * @param userMessage The user's message
     * @param conversation The conversation context
     * @return The result of the intent analysis
     */
    IntentAnalysisResult analyzeIntent(String userMessage, Conversation conversation);

    /**
     * Generates a response to the user based on execution results.
     *
     * @param conversation The conversation context
     * @param executionResult The result of executing tools
     * @return The generated response
     */
    String generateResponse(Conversation conversation, ExecutionResult executionResult);

    /**
     * Generates a response to the user based on execution feedback.
     * This method provides more detailed information for response generation.
     *
     * @param conversation The conversation context
     * @param feedback The execution feedback
     * @return The generated response
     */
    String generateResponseFromFeedback(Conversation conversation, ExecutionFeedback feedback);

    /**
     * Generates a direct response to the user without tool execution.
     * Used when no intents requiring tool execution are detected.
     *
     * @param userMessage The user's message
     * @param conversation The conversation context
     * @return The generated response
     */
    String generateDirectResponse(String userMessage, Conversation conversation);
}