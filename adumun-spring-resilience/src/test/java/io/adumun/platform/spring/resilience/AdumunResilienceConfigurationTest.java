package io.adumun.platform.spring.resilience;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdumunResilienceConfigurationTest {
    @Test
    void resilienceMechanismsAreDisabledByDefault() {
        var policy = new AdumunResilienceProperties.DependencyPolicy();

        assertThat(policy.getRetry().isEnabled()).isFalse();
        assertThat(policy.getCircuitBreaker().isEnabled()).isFalse();
        assertThat(policy.getBulkhead().isEnabled()).isFalse();
        assertThat(policy.getTimeout().isEnabled()).isFalse();
    }

    @Test
    void refusesRetryForDependencyNotDeclaredIdempotent() {
        var properties = new AdumunResilienceProperties();
        var policy = new AdumunResilienceProperties.DependencyPolicy();
        policy.getRetry().setEnabled(true);
        properties.getDependencies().put("payments", policy);

        assertThatThrownBy(() -> new AdumunResilienceRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotent=true");
    }

    @Test
    void registersConfiguredRetryAndCircuitBreaker() {
        var properties = new AdumunResilienceProperties();
        var policy = new AdumunResilienceProperties.DependencyPolicy();
        policy.setIdempotent(true);
        policy.getRetry().setEnabled(true);
        policy.getRetry().setMaxAttempts(3);
        policy.getCircuitBreaker().setEnabled(true);
        properties.getDependencies().put("catalog", policy);

        var registry = new AdumunResilienceRegistry(properties);

        assertThat(registry.retries().retry("catalog").getRetryConfig().getMaxAttempts()).isEqualTo(3);
        assertThat(registry.circuitBreakers().circuitBreaker("catalog").getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }
}
