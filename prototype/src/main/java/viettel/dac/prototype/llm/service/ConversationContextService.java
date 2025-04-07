package viettel.dac.prototype.llm.service;

import com.fasterxml.uuid.Generators;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import viettel.dac.prototype.llm.model.ConversationContext;

import java.time.Duration;

@Service
public class ConversationContextService {

    @Autowired
    private RedisTemplate<String, ConversationContext> redisTemplate;

    private final Duration TTL = Duration.ofHours(24);

    public ConversationContext createNewContext() {
        String conversationId = Generators.timeBasedGenerator().generate().toString();
        ConversationContext context = new ConversationContext(conversationId);
        saveContext(context);
        return context;
    }

    public ConversationContext getContext(String conversationId) {
        return redisTemplate.opsForValue().get("conversation:" + conversationId);
    }

    public void saveContext(ConversationContext context) {
        redisTemplate.opsForValue().set(
                "conversation:" + context.getConversationId(),
                context,
                TTL
        );
    }
}

