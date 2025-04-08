package viettel.dac.prototype.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.ChatModel;

import viettel.dac.prototype.execution.enums.ExecutionState;
import viettel.dac.prototype.execution.model.ExecutedIntent;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.llm.exception.LlmApiException;
import viettel.dac.prototype.llm.exception.LlmParsingException;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;


import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of LLM service using OpenAI's API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService implements LlmService {

    @Value("${llm.openai.model}")
    private String defaultModel;

    @Value("${llm.openai.api-key}")
    private String apiKey;

    @Value("${llm.conversation.max-history:10}")
    private int maxConversationHistory;

    private final ObjectMapper objectMapper;

    private OpenAIClient openAIClient;

    @PostConstruct
    public void init() {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        log.info("Initialized OpenAI client with model: {}", defaultModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntentAnalysisResult analyzeIntent(String userMessage, Conversation conversation) {
        try {
            String prompt = createIntentAnalysisPrompt(userMessage, conversation);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);
            return parseIntentAnalysisResponse(String.valueOf(response));
        } catch (Exception e) {
            log.error("Error analyzing intent with OpenAI", e);
            throw new LlmApiException("Failed to analyze intent", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateResponse(Conversation conversation, ExecutionResult executionResult) {
        try {
            String prompt = createResponseGenerationPrompt(conversation, executionResult);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);
            return response.toString();

        } catch (Exception e) {
            log.error("Error generating response with OpenAI", e);
            throw new LlmApiException("Failed to generate response", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateResponseFromFeedback(Conversation conversation, ExecutionFeedback feedback) {
        try {
            String prompt = createResponseFromFeedbackPrompt(conversation, feedback);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);
            return response.toString();
        } catch (Exception e) {
            log.error("Error generating response with OpenAI", e);
            throw new LlmApiException("Failed to generate response from feedback", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateDirectResponse(String userMessage, Conversation conversation) {
        try {
            String prompt = createDirectResponsePrompt(userMessage, conversation);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);
            return response.toString();
        } catch (Exception e) {
            log.error("Error generating direct response with OpenAI", e);
            throw new LlmApiException("Failed to generate direct response", e);
        }
    }

    /**
     * Helper method to convert model string to ChatModel enum.
     * Update this method with all models you need to support.
     */
    private ChatModel convertToModelEnum(String model) {
        return switch (model.toLowerCase()) {
            case "gpt-4o" -> ChatModel.GPT_4O;
            case "gpt-4o-mini" -> ChatModel.GPT_4O_MINI;
            case "o3-mini" -> ChatModel.O3_MINI;
            default -> ChatModel.GPT_4O; // Default fallback
        };
    }

    /**
     * Creates a prompt for intent analysis.
     */
    private String createIntentAnalysisPrompt(String userMessage, Conversation conversation) {
        return String.format("""
            Analyze the user message and previous context to identify intents and parameters.
            Return a JSON object with the following structure:
            {
              "confidence": 0.95,  // Overall confidence in the analysis
              "intents": [
                {
                  "intent": "toolName",  // Must match an existing tool name
                  "confidence": 0.9,     // Confidence for this specific intent
                  "parameters": {
                    "param1": "value1",
                    "param2": "value2"
                  }
                }
              ]
            }
            
            If no specific tool intent is detected, return an empty intents array.
            
            Current Message: %s
            
            Conversation History:
            %s
            """,
                userMessage,
                conversation.getFormattedHistory(maxConversationHistory)
        );
    }

    /**
     * Creates a prompt for response generation.
     */
    private String createResponseGenerationPrompt(Conversation conversation, ExecutionResult result) {
        return String.format("""
            Generate a helpful, conversational response based on the following information.
            Focus on addressing the user's original request while incorporating execution results.
            
            USER'S LATEST MESSAGE:
            %s
            
            EXECUTION RESULTS:
            %s
            
            ERRORS (if any):
            %s
            
            CONVERSATION HISTORY:
            %s
            
            Guidelines:
            1. Be natural and conversational
            2. Directly address the user's request
            3. Clearly explain any errors that occurred
            4. If all tools executed successfully, focus on summarizing the results
            5. If some tools failed, explain what worked and what didn't
            """,
                conversation.getLatestUserMessage() != null ? conversation.getLatestUserMessage().getContent() : "",
                formatExecutionResults(result),
                formatErrorSummary(result),
                conversation.getFormattedHistory(maxConversationHistory)
        );
    }

    /**
     * Creates a prompt for response generation based on execution feedback.
     */
    private String createResponseFromFeedbackPrompt(Conversation conversation, ExecutionFeedback feedback) {
        StringBuilder executedIntentsInfo = new StringBuilder();

        // Add information about successful intents
        List<ExecutedIntent> successfulIntents = feedback.getExecutedIntents().stream()
                .filter(ExecutedIntent::isSuccessful)
                .collect(Collectors.toList());

        if (!successfulIntents.isEmpty()) {
            executedIntentsInfo.append("SUCCESSFUL OPERATIONS:\n");
            for (ExecutedIntent intent : successfulIntents) {
                executedIntentsInfo.append("- ").append(intent.getIntent());
                if (intent.getResult() != null) {
                    executedIntentsInfo.append(" with result: ").append(intent.getResult());
                }
                executedIntentsInfo.append("\n");
            }
        }

        // Add information about failed intents
        List<ExecutedIntent> failedIntents = feedback.getExecutedIntents().stream()
                .filter(ExecutedIntent::hasFailed)
                .collect(Collectors.toList());

        if (!failedIntents.isEmpty()) {
            executedIntentsInfo.append("\nFAILED OPERATIONS:\n");
            for (ExecutedIntent intent : failedIntents) {
                executedIntentsInfo.append("- ").append(intent.getIntent());
                if (intent.getError() != null) {
                    executedIntentsInfo.append(": ").append(intent.getError());
                }
                executedIntentsInfo.append("\n");
            }
        }

        // Add suggestions if available
        if (feedback.getSuggestions() != null && !feedback.getSuggestions().isEmpty()) {
            executedIntentsInfo.append("\nSUGGESTIONS:\n");
            for (String suggestion : feedback.getSuggestions()) {
                executedIntentsInfo.append("- ").append(suggestion).append("\n");
            }
        }

        return String.format("""
            Generate a helpful, conversational response based on the following information.
            Focus on addressing the user's original request while incorporating execution results.
            
            USER'S LATEST MESSAGE:
            %s
            
            EXECUTION SUMMARY:
            %s
            
            EXECUTION DETAILS:
            %s
            
            CONVERSATION HISTORY:
            %s
            
            Guidelines:
            1. Be natural and conversational
            2. Directly address the user's request
            3. Clearly explain any errors that occurred
            4. If all tools executed successfully, focus on summarizing the results
            5. If some tools failed, explain what worked and what didn't
            6. If suggestions are provided, incorporate them into your response
            """,
                conversation.getLatestUserMessage() != null ? conversation.getLatestUserMessage().getContent() : "",
                feedback.isComplete() ? "All operations completed successfully" : "Some operations failed",
                executedIntentsInfo.toString(),
                conversation.getFormattedHistory(maxConversationHistory)
        );
    }

    /**
     * Creates a prompt for direct response without tool execution.
     */
    private String createDirectResponsePrompt(String userMessage, Conversation conversation) {
        return String.format("""
            Generate a helpful, conversational response to the user's message.
            There were no specific tools or operations that needed to be executed.
            
            USER'S LATEST MESSAGE:
            %s
            
            CONVERSATION HISTORY:
            %s
            
            Guidelines:
            1. Be natural and conversational
            2. Directly address the user's request
            3. If the user seems to be asking for a specific operation or tool that you cannot identify,
               suggest that they rephrase their request to be more specific
            """,
                userMessage,
                conversation.getFormattedHistory(maxConversationHistory)
        );
    }

    /**
     * Parses the LLM response to extract intent analysis.
     */
    private IntentAnalysisResult parseIntentAnalysisResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            List<Intent> intents = new ArrayList<>();
            JsonNode intentsNode = root.path("intents");

            for (JsonNode intentNode : intentsNode) {
                Intent intent = new Intent();
                intent.setIntent(intentNode.path("intent").asText());
                intent.setConfidence(intentNode.path("confidence").asDouble(0.0));
                intent.setState(ExecutionState.PENDING);

                // Parse parameters
                Map<String, Object> parameters = new HashMap<>();
                JsonNode paramsNode = intentNode.path("parameters");
                paramsNode.fields().forEachRemaining(entry ->
                        parameters.put(entry.getKey(), parseParameterValue(entry.getValue()))
                );

                intent.setParameters(parameters);
                intents.add(intent);
            }

            return IntentAnalysisResult.builder()
                    .analysisId(UUID.randomUUID().toString())
                    .intents(intents)
                    .confidence(root.path("confidence").asDouble(0.0))
                    .multiIntent(intents.size() > 1)
                    .rawResponse(response)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing LLM response: {}", response, e);
            throw new LlmParsingException("Failed to parse LLM response", e);
        }
    }

    /**
     * Formats execution results for LLM context.
     */
    private String formatExecutionResults(ExecutionResult result) {
        if (result == null || result.getExecutionRecords() == null || result.getExecutionRecords().isEmpty()) {
            return "No tools were executed.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Summary: ")
                .append(result.getSummary().getCompleted())
                .append("/")
                .append(result.getSummary().getTotalIntents())
                .append(" tools executed successfully.\n");

        return sb.toString();
    }

    /**
     * Formats error summary for LLM context.
     */
    private String formatErrorSummary(ExecutionResult result) {
        if (result == null || !result.getSummary().isHasErrors()) {
            return "No errors encountered.";
        }

        return "Some errors occurred during execution.";
    }

    /**
     * Parses a parameter value from JSON.
     */
    private Object parseParameterValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.isInt() ? node.asInt() :
                    node.isLong() ? node.asLong() :
                            node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(parseParameterValue(item));
            }
            return list;
        } else if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry ->
                    map.put(entry.getKey(), parseParameterValue(entry.getValue()))
            );
            return map;
        }
        return null;
    }
}