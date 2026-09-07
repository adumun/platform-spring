package io.adumun.onboarding.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.adumun.platform.spring.resilience.AdumunResilienceConfiguration.AdumunResilienceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ResilienceDependencyOnboardingTest {
    @Autowired AdumunResilienceRegistry registry;

    @Test
    void yamlCreatesNamedResiliencePoliciesWithoutConsumerWiring() {
        assertThat(registry.retries().getAllRetries())
                .extracting(retry -> retry.getName())
                .contains("catalog");
        assertThat(registry.circuitBreakers().getAllCircuitBreakers())
                .extracting(cb -> cb.getName())
                .contains("catalog");
        assertThat(registry.bulkheads().getAllBulkheads())
                .extracting(bulkhead -> bulkhead.getName())
                .contains("catalog");
        assertThat(registry.timeLimiters().getAllTimeLimiters())
                .extracting(timeLimiter -> timeLimiter.getName())
                .contains("catalog");
    }

    @Test
    void retryOnNonIdempotentDependencyFailsFastForConsumer() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(ResilienceDependencyApplication.class)
                        .properties(
                                "spring.main.web-application-type=none",
                                "adumun.resilience.dependencies.unsafe.idempotent=false",
                                "adumun.resilience.dependencies.unsafe.retry.enabled=true")
                        .run())
                .hasRootCauseMessage("Retry for dependency 'unsafe' requires idempotent=true");
    }
}
