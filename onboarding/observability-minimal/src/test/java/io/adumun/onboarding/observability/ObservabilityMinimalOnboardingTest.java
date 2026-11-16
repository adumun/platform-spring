package io.adumun.onboarding.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.adumun.platform.spring.observability.AdumunObservationTags;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ObservabilityMinimalOnboardingTest {
    @Autowired AdumunObservationTags tags;

    @Test
    void dependencyAndYamlProduceOperationalIdentityWithoutManualImports() {
        assertThat(tags.commonTags().stream().map(kv -> kv.getKey() + "=" + kv.getValue()).toList())
                .containsExactlyInAnyOrder(
                        "service.name=onboarding-observability",
                        "deployment.environment=test",
                        "service.version=0.0.1");
    }
}
