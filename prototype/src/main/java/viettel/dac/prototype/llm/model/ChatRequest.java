package viettel.dac.prototype.llm.model;

import lombok.Data;

@Data
public class ChatRequest {
    private String userId; // Optional: User identifier for multi-user support
    private String message; // The user's input message
}

