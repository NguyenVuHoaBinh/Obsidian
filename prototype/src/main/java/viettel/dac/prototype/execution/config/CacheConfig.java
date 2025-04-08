package viettel.dac.prototype.execution.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for caching in the execution engine.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${execution.cache.default-ttl:300}")
    private long defaultCacheTtlSeconds;

    @Value("${execution.cache.dependency-graph-ttl:3600}")
    private long dependencyGraphTtlSeconds;

    @Value("${execution.cache.tool-ttl:600}")
    private long toolCacheTtlSeconds;

    /**
     * Creates a cache manager for the execution engine.
     *
     * @param connectionFactory Redis connection factory
     * @return A configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configure serializers
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultCacheTtlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        // Cache-specific configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Configuration for dependency graph cache
        cacheConfigurations.put("dependencyGraphs", defaultCacheConfig
                .entryTtl(Duration.ofSeconds(dependencyGraphTtlSeconds)));

        // Configuration for tool cache
        cacheConfigurations.put("tools", defaultCacheConfig
                .entryTtl(Duration.ofSeconds(toolCacheTtlSeconds)));

        // Build and return the cache manager
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}