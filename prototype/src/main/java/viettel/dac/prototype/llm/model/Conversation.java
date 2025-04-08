package viettel.dac.prototype.llm.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.model.Message;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain model representing a conversation between a user and the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation implements Serializable {
    private String id;
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @Builder.Default
    private List<ExecutionResult> executionResults = new ArrayList<>();

    /**
     * Adds a user message to the conversation.
     */
    public void addUserMessage(String content) {
        messages.add(Message.builder()
                .type(MessageType.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build());
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Adds a system message to the conversation.
     */
    public void addSystemMessage(String content) {
        messages.add(Message.builder()
                .type(MessageType.SYSTEM)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build());
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Adds an execution result to the conversation.
     */
    public void addExecutionResult(ExecutionResult result) {
        this.executionResults.add(result);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Gets the latest message in the conversation.
     */
    public Message getLatestMessage() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    /**
     * Gets the latest user message in the conversation.
     */
    public Message getLatestUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getType() == MessageType.USER) {
                return messages.get(i);
            }
        }
        return null;
    }

    /**
     * Gets a formatted history of the conversation for LLM context.
     */
    public String getFormattedHistory(int maxMessages) {
        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        int startIndex = Math.max(0, messages.size() - maxMessages);

        for (int i = startIndex; i < messages.size(); i++) {
            Message message = messages.get(i);
            history.append(message.getType())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n\n");
        }

        return history.toString();
    }
}