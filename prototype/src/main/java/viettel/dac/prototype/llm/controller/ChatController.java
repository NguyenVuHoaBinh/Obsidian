package viettel.dac.prototype.llm.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.llm.model.ChatRequest;
import viettel.dac.prototype.llm.model.ChatResponse;
import viettel.dac.prototype.llm.service.ChatService;

import java.util.Map;

/**
 * REST controller for chat interactions.
 * Provides an end-to-end chat experience that integrates LLM with tool execution.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "APIs for interacting with the conversational assistant")
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Processes a chat message, potentially executing tools based on detected intents.
     *
     * @param request The chat request containing the user message
     * @param conversationId Optional conversation ID for continuing existing conversations
     * @return A response containing the assistant's reply
     */
    @PostMapping
    @Operation(
            summary = "Send a chat message",
            description = "Processes a user message, analyzes intents, executes appropriate tools, and returns a response"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message processed successfully",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Processing error")
    })
    @RateLimiter(name = "chat")
    @Timed(value = "chat.api.response.time", description = "Time taken to process a chat message")
    public ResponseEntity<ChatResponse> processMessage(
            @Parameter(description = "Chat message from the user", required = true)
            @Valid @RequestBody ChatRequest request,

            @Parameter(description = "Optional conversation ID for continuing conversations")
            @RequestHeader(value = "X-Conversation-ID", required = false) String conversationId) {

        log.info("Received chat message, conversation ID: {}", conversationId != null ? conversationId : "new");

        ChatResponse response = chatService.processMessage(request, conversationId);

        return ResponseEntity.ok()
                .header("X-Conversation-ID", response.getConversationId())
                .header("X-Requires-Follow-Up", String.valueOf(response.isRequiresFollowUp()))
                .body(response);
    }

    /**
     * Gets the history of messages for a specific conversation.
     *
     * @param conversationId The ID of the conversation
     * @return The conversation history
     */
    @GetMapping("/{conversationId}/history")
    @Operation(
            summary = "Get conversation history",
            description = "Retrieves the message history for a specific conversation"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
    })
    public ResponseEntity<Object> getConversationHistory(
            @Parameter(description = "Conversation ID", required = true)
            @PathVariable String conversationId) {

        // Note: This is a placeholder - you would need to implement this method to retrieve and return
        // the conversation history from your ConversationRepository
        log.info("Getting conversation history for: {}", conversationId);

        // Return a simple placeholder - implement actual history retrieval as needed
        return ResponseEntity.ok().body(Map.of(
                "conversationId", conversationId,
                "message", "Conversation history retrieval not implemented"
        ));
    }
}