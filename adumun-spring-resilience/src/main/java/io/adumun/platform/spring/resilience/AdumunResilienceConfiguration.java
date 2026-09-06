package io.adumun.platform.spring.resilience;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdumunResilienceProperties.class)
public class AdumunResilienceConfiguration {
    @Bean
    public AdumunResilienceRegistry adumunResilienceRegistry(AdumunResilienceProperties properties) {
        return new AdumunResilienceRegistry(properties);
    }

    public static final class AdumunResilienceRegistry {
        private final AdumunResilienceProperties properties;
        private final RetryRegistry retries = RetryRegistry.ofDefaults();
        private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.ofDefaults();
        private final BulkheadRegistry bulkheads = BulkheadRegistry.ofDefaults();
        private final TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.ofDefaults();

        public AdumunResilienceRegistry(AdumunResilienceProperties properties) {
            this.properties = properties;
            properties.getDependencies().forEach(this::register);
        }

        private void register(String name, AdumunResilienceProperties.DependencyPolicy policy) {
            if (policy.getRetry().isEnabled()) {
                if (!policy.isIdempotent()) {
                    throw new IllegalStateException("Retry for dependency '" + name + "' requires idempotent=true");
                }
                var retry = policy.getRetry();
                var interval = IntervalFunction.ofExponentialRandomBackoff(
                        retry.getInitialBackoff().toMillis(), retry.getMultiplier(), retry.getJitter());
                retries.retry(name, RetryConfig.custom()
                        .maxAttempts(retry.getMaxAttempts())
                        .intervalFunction(interval)
                        .build());
            }
            if (policy.getCircuitBreaker().isEnabled()) {
                var breaker = policy.getCircuitBreaker();
                circuitBreakers.circuitBreaker(name, CircuitBreakerConfig.custom()
                        .failureRateThreshold(breaker.getFailureRateThreshold())
                        .minimumNumberOfCalls(breaker.getMinimumNumberOfCalls())
                        .slidingWindowSize(breaker.getSlidingWindowSize())
                        .waitDurationInOpenState(breaker.getOpenStateDuration())
                        .build());
            }
            if (policy.getBulkhead().isEnabled()) {
                var bulkhead = policy.getBulkhead();
                bulkheads.bulkhead(name, BulkheadConfig.custom()
                        .maxConcurrentCalls(bulkhead.getMaxConcurrentCalls())
                        .maxWaitDuration(bulkhead.getMaxWaitDuration())
                        .build());
            }
            if (policy.getTimeout().isEnabled()) {
                timeLimiters.timeLimiter(name, TimeLimiterConfig.custom()
                        .timeoutDuration(policy.getTimeout().getDuration())
                        .build());
            }
        }

        public AdumunResilienceProperties.DependencyPolicy policy(String dependency) {
            var policy = properties.getDependencies().get(dependency);
            if (policy == null) throw new IllegalArgumentException("Unknown resilience dependency policy: " + dependency);
            return policy;
        }

        public RetryRegistry retries() { return retries; }
        public CircuitBreakerRegistry circuitBreakers() { return circuitBreakers; }
        public BulkheadRegistry bulkheads() { return bulkheads; }
        public TimeLimiterRegistry timeLimiters() { return timeLimiters; }
    }
}
