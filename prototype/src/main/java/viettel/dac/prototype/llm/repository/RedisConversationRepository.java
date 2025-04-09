package viettel.dac.prototype.llm.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import viettel.dac.prototype.execution.model.ExecutionResult;
import viettel.dac.prototype.llm.exception.ContextStorageException;
import viettel.dac.prototype.llm.model.Conversation;
import viettel.dac.prototype.llm.model.Message;
import viettel.dac.prototype.llm.model.MessageType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Optimized Redis implementation of the conversation repository.
 * Uses multiple Redis keys and data structures for more efficient storage and retrieval.
 */
@Slf4j
@Repository
public class RedisConversationRepository implements ConversationRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration conversationTtl;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Key prefixes for different data types
    private static final String META_PREFIX = "conv:meta:";
    private static final String MSGS_PREFIX = "conv:msgs:";
    private static final String FMT_PREFIX = "conv:fmt:";
    private static final String EXEC_PREFIX = "conv:exec:";
    private static final String COUNT_PREFIX = "conv:count:";

    public RedisConversationRepository(RedisTemplate<String, String> redisTemplate,
                                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.conversationTtl = Duration.ofHours(24);
    }

    @Override
    public Conversation save(Conversation conversation) {
        try {
            String id = conversation.getId();
            log.debug("Saving conversation to Redis: {}", id);

            // Update timestamps
            if (conversation.getUpdatedAt() == null) {
                conversation.setUpdatedAt(LocalDateTime.now());
            }

            // Save metadata
            saveMetadata(id, conversation);

            // Save messages
            saveMessages(id, conversation.getMessages());

            // Save execution results
            saveExecutionResults(id, conversation.getExecutionResults());

            // Expire all keys in 24 hours
            expireKeys(id);

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

            // Check if metadata exists
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(META_PREFIX + id))) {
                return Optional.empty();
            }

            // Build conversation from stored components
            Conversation conversation = new Conversation();
            conversation.setId(id);

            // Get metadata
            Map<Object, Object> metaEntries = redisTemplate.opsForHash().entries(META_PREFIX + id);
            conversation.setCreatedAt(LocalDateTime.parse((String) metaEntries.get("createdAt"), DATE_FORMAT));
            conversation.setUpdatedAt(LocalDateTime.parse((String) metaEntries.get("updatedAt"), DATE_FORMAT));

            // Get messages
            List<String> msgStrings = redisTemplate.opsForList().range(MSGS_PREFIX + id, 0, -1);
            if (msgStrings != null && !msgStrings.isEmpty()) {
                List<Message> messages = new ArrayList<>();
                for (String msgJson : msgStrings) {
                    messages.add(objectMapper.readValue(msgJson, Message.class));
                }
                conversation.setMessages(messages);
            } else {
                conversation.setMessages(new ArrayList<>());
            }

            // Get execution results
            Map<Object, Object> execEntries = redisTemplate.opsForHash().entries(EXEC_PREFIX + id);
            if (!execEntries.isEmpty()) {
                List<ExecutionResult> execResults = new ArrayList<>();
                for (Map.Entry<Object, Object> entry : execEntries.entrySet()) {
                    execResults.add(objectMapper.readValue((String) entry.getValue(), ExecutionResult.class));
                }
                conversation.setExecutionResults(execResults);
            } else {
                conversation.setExecutionResults(new ArrayList<>());
            }

            return Optional.of(conversation);
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

            LocalDateTime now = LocalDateTime.now();
            Conversation conversation = Conversation.builder()
                    .id(id)
                    .createdAt(now)
                    .updatedAt(now)
                    .messages(new ArrayList<>())
                    .executionResults(new ArrayList<>())
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

            // Delete all keys related to this conversation
            Set<String> keys = redisTemplate.keys("conv:*:" + id + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Error deleting conversation from Redis", e);
            throw new ContextStorageException("Failed to delete conversation", e);
        }
    }

    /**
     * Retrieves the formatted message history ready for LLM prompt inclusion.
     * Optimized to provide pre-formatted message history.
     *
     * @param id The conversation ID
     * @param maxMessages The maximum number of messages to include
     * @return Formatted message history string
     */
    public String getFormattedHistory(String id, int maxMessages) {
        try {
            // Check if we have a cached formatted history
            String fmtKey = FMT_PREFIX + id + ":" + maxMessages;
            String cached = redisTemplate.opsForValue().get(fmtKey);

            if (cached != null) {
                return cached;
            }

            // If not cached, get messages and format them
            List<String> msgStrings = redisTemplate.opsForList().range(
                    MSGS_PREFIX + id,
                    -maxMessages,
                    -1
            );

            if (msgStrings == null || msgStrings.isEmpty()) {
                return "";
            }

            StringBuilder history = new StringBuilder();

            for (String msgJson : msgStrings) {
                Message message = objectMapper.readValue(msgJson, Message.class);
                history.append(message.getType())
                        .append(": ")
                        .append(message.getContent())
                        .append("\n\n");
            }

            String formatted = history.toString();

            // Cache the formatted history with TTL
            redisTemplate.opsForValue().set(fmtKey, formatted, conversationTtl);

            return formatted;
        } catch (Exception e) {
            log.error("Error getting formatted history", e);
            throw new ContextStorageException("Failed to get formatted history", e);
        }
    }

    /**
     * Adds a message to the conversation and updates formatted history caches.
     *
     * @param id The conversation ID
     * @param message The message to add
     */
    public void addMessage(String id, Message message) {
        try {
            // Add to message list
            String msgJson = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(MSGS_PREFIX + id, msgJson);

            // Update message count
            redisTemplate.opsForValue().increment(COUNT_PREFIX + id);

            // Invalidate all formatted history caches for this conversation
            Set<String> fmtKeys = redisTemplate.keys(FMT_PREFIX + id + ":*");
            if (fmtKeys != null && !fmtKeys.isEmpty()) {
                redisTemplate.delete(fmtKeys);
            }

            // Update last updated timestamp
            redisTemplate.opsForHash().put(
                    META_PREFIX + id,
                    "updatedAt",
                    LocalDateTime.now().format(DATE_FORMAT)
            );

            // Reset TTL
            expireKeys(id);
        } catch (Exception e) {
            log.error("Error adding message", e);
            throw new ContextStorageException("Failed to add message", e);
        }
    }

    /**
     * Adds a user message to the conversation.
     *
     * @param id The conversation ID
     * @param content The message content
     */
    public void addUserMessage(String id, String content) {
        Message message = Message.builder()
                .type(MessageType.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        addMessage(id, message);
    }

    /**
     * Adds a system message to the conversation.
     *
     * @param id The conversation ID
     * @param content The message content
     */
    public void addSystemMessage(String id, String content) {
        Message message = Message.builder()
                .type(MessageType.SYSTEM)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        addMessage(id, message);
    }

    /**
     * Adds an execution result to the conversation.
     *
     * @param id The conversation ID
     * @param result The execution result
     */
    public void addExecutionResult(String id, ExecutionResult result) {
        try {
            String execJson = objectMapper.writeValueAsString(result);
            redisTemplate.opsForHash().put(
                    EXEC_PREFIX + id,
                    result.getAnalysisId(),
                    execJson
            );

            // Update last updated timestamp
            redisTemplate.opsForHash().put(
                    META_PREFIX + id,
                    "updatedAt",
                    LocalDateTime.now().format(DATE_FORMAT)
            );

            // Reset TTL
            expireKeys(id);
        } catch (Exception e) {
            log.error("Error adding execution result", e);
            throw new ContextStorageException("Failed to add execution result", e);
        }
    }

    /**
     * Gets the message count for a conversation.
     *
     * @param id The conversation ID
     * @return The message count
     */
    public long getMessageCount(String id) {
        String countStr = redisTemplate.opsForValue().get(COUNT_PREFIX + id);
        if (countStr != null) {
            return Long.parseLong(countStr);
        }

        // If count not found, calculate it from the list
        Long size = redisTemplate.opsForList().size(MSGS_PREFIX + id);
        return size != null ? size : 0;
    }

    // Helper methods

    private void saveMetadata(String id, Conversation conversation) {
        Map<String, String> meta = new HashMap<>();
        meta.put("createdAt", conversation.getCreatedAt().format(DATE_FORMAT));
        meta.put("updatedAt", conversation.getUpdatedAt().format(DATE_FORMAT));

        redisTemplate.opsForHash().putAll(META_PREFIX + id, meta);
    }

    private void saveMessages(String id, List<Message> messages) throws JsonProcessingException {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // Delete existing messages
        redisTemplate.delete(MSGS_PREFIX + id);

        // Add all messages in order
        for (Message message : messages) {
            String msgJson = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(MSGS_PREFIX + id, msgJson);
        }

        // Update count
        redisTemplate.opsForValue().set(COUNT_PREFIX + id, String.valueOf(messages.size()));

        // Delete all formatted history caches
        Set<String> fmtKeys = redisTemplate.keys(FMT_PREFIX + id + ":*");
        if (fmtKeys != null && !fmtKeys.isEmpty()) {
            redisTemplate.delete(fmtKeys);
        }
    }

    private void saveExecutionResults(String id, List<ExecutionResult> results) throws JsonProcessingException {
        if (results == null || results.isEmpty()) {
            return;
        }

        // Delete existing execution results
        redisTemplate.delete(EXEC_PREFIX + id);

        // Add all execution results
        Map<String, String> execMap = new HashMap<>();
        for (ExecutionResult result : results) {
            execMap.put(result.getAnalysisId(), objectMapper.writeValueAsString(result));
        }

        redisTemplate.opsForHash().putAll(EXEC_PREFIX + id, execMap);
    }

    private void expireKeys(String id) {
        // Set expiry on all keys for this conversation
        redisTemplate.expire(META_PREFIX + id, conversationTtl);
        redisTemplate.expire(MSGS_PREFIX + id, conversationTtl);
        redisTemplate.expire(EXEC_PREFIX + id, conversationTtl);
        redisTemplate.expire(COUNT_PREFIX + id, conversationTtl);

        // Also expire any formatted history keys
        Set<String> fmtKeys = redisTemplate.keys(FMT_PREFIX + id + ":*");
        if (fmtKeys != null && !fmtKeys.isEmpty()) {
            for (String key : fmtKeys) {
                redisTemplate.expire(key, conversationTtl);
            }
        }
    }
}