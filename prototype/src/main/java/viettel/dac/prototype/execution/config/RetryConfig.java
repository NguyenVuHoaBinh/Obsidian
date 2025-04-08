package viettel.dac.prototype.execution.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.Collections;

/**
 * Configuration class for retry mechanisms and rate limiting.
 */
@Configuration
@EnableRetry
public class RetryConfig {

    @Value("${execution.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${execution.retry.initial-backoff:1000}")
    private long initialBackoffMillis;

    @Value("${execution.retry.max-backoff:10000}")
    private long maxBackoffMillis;

    @Value("${execution.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${execution.ratelimit.limit-for-period:10}")
    private int rateLimitForPeriod;

    @Value("${execution.ratelimit.limit-refresh-period:1}")
    private int rateLimitRefreshPeriodSeconds;

    @Value("${execution.ratelimit.timeout-duration:5}")
    private int rateLimitTimeoutSeconds;

    /**
     * Creates a retry template for programmatic retry operations.
     *
     * @return A configured RetryTemplate instance
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialBackoffMillis);
        backOffPolicy.setMaxInterval(maxBackoffMillis);
        backOffPolicy.setMultiplier(backoffMultiplier);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Configure retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                maxRetryAttempts,
                Collections.singletonMap(Exception.class, true)
        );
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    /**
     * Creates a registry of rate limiters for the execution engine.
     *
     * @return A configured RateLimiterRegistry
     */
    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(rateLimitForPeriod)
                .limitRefreshPeriod(Duration.ofSeconds(rateLimitRefreshPeriodSeconds))
                .timeoutDuration(Duration.ofSeconds(rateLimitTimeoutSeconds))
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);

        // Create a rate limiter for the execution engine API
        registry.rateLimiter("execution");

        return registry;
    }
}