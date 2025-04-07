package viettel.dac.prototype.llm.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import viettel.dac.prototype.execution.model.ExecutionResult;

import java.util.ArrayList;
import java.util.List;

@Data
@RedisHash("ConversationContext")
public class ConversationContext {
    @Id
    private String conversationId;
    private List<String> messageHistory = new ArrayList<>();
    private List<ExecutionResult> executionHistory = new ArrayList<>();

    public ConversationContext() {}

    public ConversationContext(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getLatestMessage() {
        return messageHistory.isEmpty() ?
                "" : messageHistory.get(messageHistory.size() - 1);
    }
}
