package viettel.dac.prototype.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Optimized Redis configuration for conversation storage.
 * Uses string-based Redis operations for more efficient data handling.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate for String keys and values.
     * This is more efficient than using object serialization.
     *
     * @param connectionFactory The Redis connection factory
     * @return A RedisTemplate configured for String keys and values
     */
    @Bean
    @Primary
    public RedisTemplate<String, String> customStringRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for both keys and values
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * Creates an ObjectMapper configured for conversation serialization/deserialization.
     *
     * @return A configured ObjectMapper instance
     */
    @Bean
    public ObjectMapper conversationObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}