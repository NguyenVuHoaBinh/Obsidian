package viettel.dac.prototype.llm.repository;


import com.fasterxml.uuid.Generators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import viettel.dac.prototype.llm.exception.ContextStorageException;
import viettel.dac.prototype.llm.model.Conversation;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Redis implementation of the conversation repository.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisConversationRepository implements ConversationRepository {

    private final RedisTemplate<String, Conversation> redisTemplate;
    private final Duration conversationTtl = Duration.ofHours(24);

    /**
     * Key prefix for Redis keys.
     */
    private static final String KEY_PREFIX = "conversation:";

    @Override
    public Conversation save(Conversation conversation) {
        try {
            String key = KEY_PREFIX + conversation.getId();
            log.debug("Saving conversation to Redis: {}", key);

            // Update the updatedAt timestamp
            if (conversation.getUpdatedAt() == null) {
                conversation.setUpdatedAt(LocalDateTime.now());
            }

            redisTemplate.opsForValue().set(key, conversation, conversationTtl);
            return conversation;
        } catch (Exception e) {
            log.error("Error saving conversation to Redis", e);
            throw new ContextStorageException("Failed to save conversation", e);
        }
    }

    @Override
    public Optional<Conversation> findById(String id) {
        try {
            log.debug("Finding conversation in Redis: {}", id);
            String key = KEY_PREFIX + id;
            Conversation conversation = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(conversation);
        } catch (Exception e) {
            log.error("Error finding conversation in Redis", e);
            throw new ContextStorageException("Failed to find conversation", e);
        }
    }

    @Override
    public Conversation createNew() {
        try {
            String id = Generators.timeBasedGenerator().generate().toString();
            log.info("Creating new conversation with ID: {}", id);

            Conversation conversation = Conversation.builder()
                    .id(id)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            save(conversation);
            return conversation;
        } catch (Exception e) {
            log.error("Error creating new conversation", e);
            throw new ContextStorageException("Failed to create new conversation", e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            log.debug("Deleting conversation from Redis: {}", id);
            String key = KEY_PREFIX + id;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error deleting conversation from Redis", e);
            throw new ContextStorageException("Failed to delete conversation", e);
        }
    }
}
