package io.adumun.platform.spring.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("adumun.resilience")
public class AdumunResilienceProperties {
    private final Map<String, DependencyPolicy> dependencies = new LinkedHashMap<>();

    public Map<String, DependencyPolicy> getDependencies() {
        return dependencies;
    }

    public static class DependencyPolicy {
        private boolean idempotent = false;
        private final Retry retry = new Retry();
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();
        private final Bulkhead bulkhead = new Bulkhead();
        private final Timeout timeout = new Timeout();

        public boolean isIdempotent() { return idempotent; }
        public void setIdempotent(boolean idempotent) { this.idempotent = idempotent; }
        public Retry getRetry() { return retry; }
        public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
        public Bulkhead getBulkhead() { return bulkhead; }
        public Timeout getTimeout() { return timeout; }
    }

    public static class Retry {
        private boolean enabled = false;
        private int maxAttempts = 2;
        private Duration initialBackoff = Duration.ofMillis(100);
        private double multiplier = 2.0d;
        private double jitter = 0.25d;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 2) throw new IllegalArgumentException("retry.maxAttempts must be >= 2 when retry is enabled");
            this.maxAttempts = maxAttempts;
        }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) {
            if (multiplier < 1.0d) throw new IllegalArgumentException("retry.multiplier must be >= 1.0");
            this.multiplier = multiplier;
        }
        public double getJitter() { return jitter; }
        public void setJitter(double jitter) {
            if (jitter < 0.0d || jitter > 1.0d) throw new IllegalArgumentException("retry.jitter must be between 0.0 and 1.0");
            this.jitter = jitter;
        }
    }

    public static class CircuitBreaker {
        private boolean enabled = false;
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 10;
        private int slidingWindowSize = 20;
        private Duration openStateDuration = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public Duration getOpenStateDuration() { return openStateDuration; }
        public void setOpenStateDuration(Duration openStateDuration) { this.openStateDuration = openStateDuration; }
    }

    public static class Bulkhead {
        private boolean enabled = false;
        private int maxConcurrentCalls = 25;
        private Duration maxWaitDuration = Duration.ZERO;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }
        public Duration getMaxWaitDuration() { return maxWaitDuration; }
        public void setMaxWaitDuration(Duration maxWaitDuration) { this.maxWaitDuration = maxWaitDuration; }
    }

    public static class Timeout {
        private boolean enabled = false;
        private Duration duration = Duration.ofSeconds(2);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getDuration() { return duration; }
        public void setDuration(Duration duration) { this.duration = duration; }
    }
}
