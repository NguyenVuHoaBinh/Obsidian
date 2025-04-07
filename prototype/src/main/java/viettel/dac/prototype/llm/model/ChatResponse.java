package viettel.dac.prototype.llm.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {
    private String message;
    private String conversationId;
    private boolean requiresFollowUp;
}

