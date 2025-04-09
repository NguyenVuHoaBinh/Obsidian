package viettel.dac.prototype.llm.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.Message;
import viettel.dac.prototype.llm.repository.RedisConversationRepository;

/**
 * Utility methods for working with conversations in the LLM context.
 * Provides efficient access to conversation data for LLM prompts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationUtils {

    private final RedisConversationRepository repository;

    @Value("${llm.conversation.max-history:10}")
    private int maxConversationHistory;

    /**
     * Gets the formatted message history for a conversation.
     * Uses optimized Redis storage to efficiently retrieve only what's needed.
     *
     * @param conversation The conversation object
     * @return Formatted message history string ready for LLM prompt
     */
    public String getFormattedHistory(Conversation conversation) {
        return getFormattedHistory(conversation, maxConversationHistory);
    }

    /**
     * Gets the formatted message history for a conversation with custom length.
     * Uses optimized Redis storage to efficiently retrieve only what's needed.
     *
     * @param conversation The conversation object
     * @param maxMessages Maximum number of messages to include
     * @return Formatted message history string ready for LLM prompt
     */
    public String getFormattedHistory(Conversation conversation, int maxMessages) {
        if (conversation == null || conversation.getId() == null) {
            return "";
        }

        return repository.getFormattedHistory(conversation.getId(), maxMessages);
    }

    /**
     * Adds a user message to the conversation.
     * Updates both the Conversation object and Redis storage.
     *
     * @param conversation The conversation object
     * @param content The message content
     */
    public void addUserMessage(Conversation conversation, String content) {
        if (conversation == null || conversation.getId() == null) {
            log.warn("Cannot add user message to null conversation");
            return;
        }

        // Update the repository
        repository.addUserMessage(conversation.getId(), content);

        // Update the in-memory object
        Message message = Message.builder()
                .type(viettel.dac.prototype.llm.model.MessageType.USER)
                .content(content)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        if (conversation.getMessages() == null) {
            conversation.setMessages(new java.util.ArrayList<>());
        }

        conversation.getMessages().add(message);
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
    }

    /**
     * Adds a system message to the conversation.
     * Updates both the Conversation object and Redis storage.
     *
     * @param conversation The conversation object
     * @param content The message content
     */
    public void addSystemMessage(Conversation conversation, String content) {
        if (conversation == null || conversation.getId() == null) {
            log.warn("Cannot add system message to null conversation");
            return;
        }

        // Update the repository
        repository.addSystemMessage(conversation.getId(), content);

        // Update the in-memory object
        Message message = Message.builder()
                .type(viettel.dac.prototype.llm.model.MessageType.SYSTEM)
                .content(content)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        if (conversation.getMessages() == null) {
            conversation.setMessages(new java.util.ArrayList<>());
        }

        conversation.getMessages().add(message);
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
    }

    /**
     * Adds an execution result to the conversation.
     * Updates both the Conversation object and Redis storage.
     *
     * @param conversation The conversation object
     * @param result The execution result
     */
    public void addExecutionResult(Conversation conversation, ExecutionResult result) {
        if (conversation == null || conversation.getId() == null || result == null) {
            log.warn("Cannot add execution result to null conversation");
            return;
        }

        // Update the repository
        repository.addExecutionResult(conversation.getId(), result);

        // Update the in-memory object
        if (conversation.getExecutionResults() == null) {
            conversation.setExecutionResults(new java.util.ArrayList<>());
        }

        conversation.getExecutionResults().add(result);
        conversation.setUpdatedAt(java.time.LocalDateTime.now());
    }

    /**
     * Gets the latest user message in the conversation.
     *
     * @param conversation The conversation object
     * @return The latest user message, or null if none exists
     */
    public Message getLatestUserMessage(Conversation conversation) {
        if (conversation == null || conversation.getMessages() == null || conversation.getMessages().isEmpty()) {
            return null;
        }

        for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
            Message message = conversation.getMessages().get(i);
            if (message.getType() == viettel.dac.prototype.llm.model.MessageType.USER) {
                return message;
            }
        }

        return null;
    }

    /**
     * Gets the message count for a conversation.
     *
     * @param conversation The conversation object
     * @return The number of messages in the conversation
     */
    public long getMessageCount(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return 0;
        }

        return repository.getMessageCount(conversation.getId());
    }
}