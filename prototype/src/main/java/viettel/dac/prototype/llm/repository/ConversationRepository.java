package viettel.dac.prototype.llm.repository;


import viettel.dac.prototype.llm.model.Conversation;

import java.util.Optional;

/**
 * Repository interface for conversation persistence operations.
 */
public interface ConversationRepository {

    /**
     * Saves a conversation.
     *
     * @param conversation The conversation to save
     * @return The saved conversation
     */
    Conversation save(Conversation conversation);

    /**
     * Finds a conversation by its ID.
     *
     * @param id The ID of the conversation to find
     * @return An Optional containing the conversation if found, or empty if not found
     */
    Optional<Conversation> findById(String id);

    /**
     * Creates a new conversation with a generated ID.
     *
     * @return The newly created conversation
     */
    Conversation createNew();

    /**
     * Deletes a conversation by its ID.
     *
     * @param id The ID of the conversation to delete
     */
    void deleteById(String id);
}