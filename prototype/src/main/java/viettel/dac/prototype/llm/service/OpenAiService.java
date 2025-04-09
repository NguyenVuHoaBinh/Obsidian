package viettel.dac.prototype.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.execution.enums.ExecutionState;
import viettel.dac.prototype.execution.model.ExecutedIntent;
import viettel.dac.prototype.execution.model.ExecutionFeedback;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.llm.exception.LlmApiException;
import viettel.dac.prototype.llm.exception.LlmParsingException;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.IntentAnalysisResult;
import viettel.dac.prototype.llm.model.Message;
import viettel.dac.prototype.llm.utils.ConversationUtils;
import viettel.dac.prototype.tool.service.ToolService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized implementation of LLM service using OpenAI's API.
 * Uses the ConversationUtils to efficiently retrieve conversation data for prompts.
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
    private final ToolService toolService;
    private final ConversationUtils conversationUtils;

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
            log.info("Sending intent analysis prompt to OpenAI\n" + prompt);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);

            // Extract the text content from the response
            String responseText = extractTextFromResponse(response);
            log.debug("Received intent analysis response from OpenAI");

            return parseIntentAnalysisResponse(responseText);
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
            log.info("Sending response generation prompt to OpenAI\n" + prompt);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);

            // Extract and return the text content
            return extractTextFromResponse(response);

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
            log.info("Sending feedback-based response prompt to OpenAI\n" + prompt);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);

            // Extract and return the text content
            return extractTextFromResponse(response);
        } catch (Exception e) {
            log.error("Error generating response from feedback with OpenAI", e);
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
            log.info("Sending direct response prompt to OpenAI\n" + prompt);

            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(convertToModelEnum(defaultModel))
                    .input(prompt)
                    .build();

            Response response = openAIClient.responses().create(params);

            // Extract and return the text content
            return extractTextFromResponse(response);
        } catch (Exception e) {
            log.error("Error generating direct response with OpenAI", e);
            throw new LlmApiException("Failed to generate direct response", e);
        }
    }

    /**
     * Helper method to extract the text content from the OpenAI response.
     */
    private String extractTextFromResponse(Response response) {
        StringBuilder textBuilder = new StringBuilder();

        try {
            response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .forEach(outputText -> textBuilder.append(outputText.text()));

            return textBuilder.toString();
        } catch (Exception e) {
            log.error("Error extracting text from response", e);
            throw new LlmParsingException("Failed to extract text from response", e);
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
     * Creates a prompt for intent analysis with tools information in JSON format.
     */
    private String createIntentAnalysisPrompt(String userMessage, Conversation conversation) {
        try {
            // Get tools in JSON format and format it for readability
            String toolsJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toolService.getToolsForPrompt());

            return String.format("""
            # Intent Analysis Task
            
            Your task is to analyze the user's message to identify which tool(s) they want to use and extract parameter values.
            
            ## Available Tools (JSON Format)
            
            ```json
            %s
            ```
            
            ## Instructions
            
            1. Identify which tool(s) from the above JSON the user wants to use based on their message
            2. Extract parameter values from the user's message that match the tool's parameters
            3. For each identified tool, check if all required parameters are present
            4. If no specific tool intent is detected, return an empty intents array
            5. Carefully check the field Dependency of the tool, if missing, put the missing tool infront of the original tool, then retrieve missing parameters from the context or conversation history to complete the requirements.
            
            ## Response Format
            
            Return a valid JSON object with this structure:
            ```json
            {
              "confidence": 0.95,
              "intents": [
                {
                  "intent": "toolName",
                  "confidence": 0.9,
                  "parameters": {
                    "param1": "value1",
                    "param2": "value2"
                  }
                }
              ]
            }
            ```
            
            ## User's Current Message
            
            %s
            
            ## Conversation History
            
            %s
            """,
                    toolsJson,
                    userMessage,
                    conversationUtils.getFormattedHistory(conversation)
            );
        } catch (Exception e) {
            log.error("Error creating intent analysis prompt", e);
            throw new LlmApiException("Failed to create intent analysis prompt", e);
        }
    }

    /**
     * Creates a prompt for response generation using the optimized conversation history.
     */
    private String createResponseGenerationPrompt(Conversation conversation, ExecutionResult result) {
        return String.format("""
            Generate a helpful, conversational response based on the following information. Return response in Vietnamese and in short.
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
                getLatestUserMessageContent(conversation),
                formatExecutionResults(result),
                formatErrorSummary(result),
                conversationUtils.getFormattedHistory(conversation)
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
            Generate a helpful, conversational response based on the following information. Return response in Vietnamese and in short.
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
                getLatestUserMessageContent(conversation),
                feedback.isComplete() ? "All operations completed successfully" : "Some operations failed",
                executedIntentsInfo.toString(),
                conversationUtils.getFormattedHistory(conversation)
        );
    }

    /**
     * Creates a prompt for direct response without tool execution.
     */
    private String createDirectResponsePrompt(String userMessage, Conversation conversation) {
        return String.format("""
            Generate a helpful, conversational response to the user's message. Return response in Vietnamese and in short.
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
                conversationUtils.getFormattedHistory(conversation)
        );
    }

    /**
     * Gets the content of the latest user message.
     */
    private String getLatestUserMessageContent(Conversation conversation) {
        Message latestUserMessage = conversationUtils.getLatestUserMessage(conversation);
        return latestUserMessage != null ? latestUserMessage.getContent() : "";
    }

    /**
     * Parses the LLM response to extract intent analysis.
     */
    private IntentAnalysisResult parseIntentAnalysisResponse(String response) {
        try {
            // Strip Markdown code block delimiters if present
            String jsonContent = response;
            if (response.startsWith("```")) {
                // Find the first newline after the opening delimiter
                int startIndex = response.indexOf('\n');
                if (startIndex != -1) {
                    // Find the closing delimiter
                    int endIndex = response.lastIndexOf("```");
                    if (endIndex > startIndex) {
                        // Extract just the JSON content
                        jsonContent = response.substring(startIndex + 1, endIndex).trim();
                    }
                }
            }

            JsonNode root = objectMapper.readTree(jsonContent);

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