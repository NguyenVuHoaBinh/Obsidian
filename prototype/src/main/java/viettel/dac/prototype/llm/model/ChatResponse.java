package viettel.dac.prototype.llm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Enhanced DTO for chat response with execution timing details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String message;
    private String conversationId;
    private boolean requiresFollowUp;

    // New field to track execution details for UI display
    private List<Map<String, Object>> executionDetails;

    // Constructor compatible with the original one
    public ChatResponse(String message, String conversationId, boolean requiresFollowUp) {
        this.message = message;
        this.conversationId = conversationId;
        this.requiresFollowUp = requiresFollowUp;
    }
}