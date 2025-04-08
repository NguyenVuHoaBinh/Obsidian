package viettel.dac.prototype.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import viettel.dac.prototype.llm.model.Conversation;

/**
 * Redis configuration for conversation storage.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate for Conversation objects.
     *
     * @param connectionFactory The Redis connection factory
     * @return A RedisTemplate configured for Conversation objects
     */
    @Bean
    public RedisTemplate<String, Conversation> conversationRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Conversation> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for keys
        template.setKeySerializer(new StringRedisSerializer());

        // Use GenericJackson2JsonRedisSerializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        // Configure hash operations
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();

        return template;
    }
}