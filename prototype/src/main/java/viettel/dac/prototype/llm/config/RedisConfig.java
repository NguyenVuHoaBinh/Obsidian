package viettel.dac.prototype.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import viettel.dac.prototype.llm.model.ConversationContext;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ConversationContext> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ConversationContext> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
