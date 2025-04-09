package viettel.dac.prototype.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.DependencyRepository;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Optimized service for processing chat messages using the improved Redis storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmService llmService;
    private final RedisConversationRepository conversationRepository;
    private final ExecutionEngineService executionEngineService;
    private final ConversationUtils conversationUtils;
    private final ToolRepository toolRepository;
    private final DependencyRepository dependencyRepository;

    /**
     * Processes a chat message and returns a response.
     * Uses the optimized Redis repository for more efficient conversation management.
     *
     * @param request The chat request containing the user message
     * @param conversationId Optional conversation ID for existing conversations
     * @return A chat response containing the system's response
     */
    @Transactional(readOnly = true)
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

                return new ChatResponse(directResponse, conversation.getId(), false);
            }

            // Add dependency validation here - before executing tools
            Optional<String> missingDependenciesMessage = validateDependencies(analysisResult.getIntents());
            if (missingDependenciesMessage.isPresent()) {
                log.warn("Missing dependencies detected: {}", missingDependenciesMessage.get());

                // Create a user-friendly response about missing dependencies
                String dependencyErrorResponse = generateDependencyErrorResponse(
                        request.getMessage(), missingDependenciesMessage.get(), conversation);

                // Add system response to conversation
                conversationUtils.addSystemMessage(conversation, dependencyErrorResponse);

                return new ChatResponse(dependencyErrorResponse, conversation.getId(), true);
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
     * Validates dependencies for all intents, with special handling for OR relationships.
     *
     * @param intents The list of intents to validate
     * @return Optional error message if dependencies are missing, empty if all dependencies are satisfied
     */
    private Optional<String> validateDependencies(List<Intent> intents) {
        log.debug("Validating dependencies for {} intents", intents.size());

        // Collect all tool names from intents
        Set<String> availableToolNames = intents.stream()
                .map(Intent::getIntent)
                .collect(Collectors.toSet());

        // Map to collect missing dependencies for each tool
        Map<String, List<String>> missingDependencies = new HashMap<>();
        Map<String, Tool> missingToolInfo = new HashMap<>();

        // Define special OR dependency relationships
        Map<String, List<List<String>>> orDependencies = new HashMap<>();

        // Add known OR relationships
        List<List<String>> paymentMethodOrGroups = new ArrayList<>();
        paymentMethodOrGroups.add(Arrays.asList("add_product_to_order", "create_quick_order"));
        orDependencies.put("select_payment_method", paymentMethodOrGroups);

        // Process each intent
        for (Intent intent : intents) {
            String toolName = intent.getIntent();

            // Skip tools with no dependencies
            Tool tool = toolRepository.findByName(toolName)
                    .orElse(null);

            if (tool == null) {
                log.warn("Tool '{}' not found in repository", toolName);
                continue;
            }

            List<Dependency> dependencies = dependencyRepository.findByTool(tool);
            if (dependencies.isEmpty()) {
                log.debug("Tool '{}' has no dependencies, skipping", toolName);
                continue;
            }

            // Get all dependency names for this tool
            List<String> dependencyNames = dependencies.stream()
                    .map(dep -> dep.getDependsOn().getName())
                    .collect(Collectors.toList());

            log.debug("Tool '{}' has dependencies: {}", toolName, dependencyNames);

            // Check if this tool has special OR dependency relationships
            if (orDependencies.containsKey(toolName)) {
                log.debug("Tool '{}' has OR dependency groups", toolName);

                // Handle OR dependency groups
                for (List<String> orGroup : orDependencies.get(toolName)) {
                    boolean hasAnyDependency = false;
                    List<String> missingFromGroup = new ArrayList<>();

                    // Check if any of the OR dependencies are available
                    for (String depName : orGroup) {
                        if (availableToolNames.contains(depName)) {
                            hasAnyDependency = true;
                            log.debug("Tool '{}' has OR dependency '{}' satisfied", toolName, depName);
                            break;
                        } else {
                            missingFromGroup.add(depName);
                        }
                    }

                    // If none of the OR dependencies are available, report them as missing
                    if (!hasAnyDependency && !missingFromGroup.isEmpty()) {
                        missingDependencies.put(toolName, missingFromGroup);

                        // Collect tool info for error message
                        for (String missingToolName : missingFromGroup) {
                            if (!missingToolInfo.containsKey(missingToolName)) {
                                Tool missingTool = toolRepository.findByName(missingToolName).orElse(null);
                                if (missingTool != null) {
                                    missingToolInfo.put(missingToolName, missingTool);
                                }
                            }
                        }

                        log.warn("Tool '{}' is missing all OR dependencies: {}",
                                toolName, missingFromGroup);
                    }
                }
            } else {
                // Handle standard AND dependencies
                List<String> missingFromTool = dependencyNames.stream()
                        .filter(depName -> !availableToolNames.contains(depName))
                        .collect(Collectors.toList());

                if (!missingFromTool.isEmpty()) {
                    missingDependencies.put(toolName, missingFromTool);

                    // Collect tool info for error message
                    for (String missingToolName : missingFromTool) {
                        if (!missingToolInfo.containsKey(missingToolName)) {
                            Tool missingTool = toolRepository.findByName(missingToolName).orElse(null);
                            if (missingTool != null) {
                                missingToolInfo.put(missingToolName, missingTool);
                            }
                        }
                    }

                    log.warn("Tool '{}' is missing dependencies: {}",
                            toolName, missingFromTool);
                }
            }
        }

        // If there are missing dependencies, generate an error message
        if (!missingDependencies.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("Cannot execute all requested tools due to missing dependencies:\n");

            for (Map.Entry<String, List<String>> entry : missingDependencies.entrySet()) {
                String toolName = entry.getKey();
                List<String> missing = entry.getValue();
                Tool tool = toolRepository.findByName(toolName).orElse(null);

                if (tool != null) {
                    errorMsg.append("\n• ").append(toolName)
                            .append(" (").append(tool.getDescription()).append(")");

                    // Handle OR dependencies differently
                    if (orDependencies.containsKey(toolName)) {
                        errorMsg.append(" requires at least ONE of these tools: ");
                    } else {
                        errorMsg.append(" requires these tools: ");
                    }

                    boolean first = true;
                    for (String missingToolName : missing) {
                        Tool missingTool = missingToolInfo.get(missingToolName);

                        if (!first) {
                            errorMsg.append(", ");
                        }
                        first = false;

                        errorMsg.append(missingToolName);
                        if (missingTool != null) {
                            errorMsg.append(" (").append(missingTool.getDescription()).append(")");
                        }
                    }
                }
            }

            return Optional.of(errorMsg.toString());
        }

        return Optional.empty();
    }

    /**
     * Generates a user-friendly response when dependencies are missing.
     *
     * @param userMessage The original user message
     * @param missingDependenciesInfo Information about missing dependencies
     * @param conversation The current conversation
     * @return A response message explaining the missing dependencies
     */
    private String generateDependencyErrorResponse(
            String userMessage, String missingDependenciesInfo, Conversation conversation) {

        // Use LLM to generate a friendly response explaining the dependencies
        String prompt = String.format(
                "The user is trying to perform an operation but some dependencies are missing. " +
                        "Generate a friendly, helpful response explaining what's needed.\n\n" +
                        "User message: %s\n\n" +
                        "Missing dependencies info: %s\n\n" +
                        "Explain in a friendly way why their request couldn't be completed and what " +
                        "they need to do first. Don't use technical terms like 'dependencies' - instead " +
                        "explain the logical sequence of steps needed.",
                userMessage, missingDependenciesInfo
        );

        try {
            // Try to use the LLM for a natural language response
            return llmService.generateDirectResponse(prompt, conversation);
        } catch (Exception e) {
            log.warn("Failed to generate friendly dependency error response", e);

            // Fallback to a simple message with the technical details
            return "I can't complete that action right now because some prerequisite steps are missing:\n\n" +
                    missingDependenciesInfo + "\n\n" +
                    "Please complete these steps first before trying again.";
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