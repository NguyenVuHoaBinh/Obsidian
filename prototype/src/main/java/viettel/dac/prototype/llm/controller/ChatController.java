package viettel.dac.prototype.llm.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import viettel.dac.prototype.llm.model.ChatResponse;
import viettel.dac.prototype.llm.service.ChatService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> handleChatMessage(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Conversation-Id", required = false) String conversationId) {

        ChatResponse response = chatService.handleMessage(conversationId, request.getMessage());

        return ResponseEntity.ok()
                .header("X-Conversation-Id", response.getConversationId())
                .body(response);
    }
}

