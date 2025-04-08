package viettel.dac.prototype.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

        // Configure ObjectMapper with JavaTimeModule
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Use StringRedisSerializer for keys
        template.setKeySerializer(new StringRedisSerializer());

        // Use customized GenericJackson2JsonRedisSerializer for values
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(valueSerializer);

        // Configure hash operations
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();

        return template;
    }
}