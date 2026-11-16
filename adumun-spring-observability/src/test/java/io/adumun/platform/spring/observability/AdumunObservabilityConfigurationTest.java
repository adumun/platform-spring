package io.adumun.platform.spring.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdumunObservabilityConfigurationTest {
    @Test
    void exposesStableCommonOperationalTags() {
        var properties = new AdumunObservabilityProperties();
        properties.setServiceName("orders");
        properties.setEnvironment("test");
        properties.setBuildVersion("abc123");

        var tags = new AdumunObservationTags(properties).commonTags();

        assertThat(tags.stream().map(kv -> kv.getKey() + "=" + kv.getValue()).toList())
                .containsExactlyInAnyOrder(
                        "service.name=orders",
                        "deployment.environment=test",
                        "service.version=abc123");
    }

    @Test
    void rejectsInvalidSamplingProbability() {
        var properties = new AdumunObservabilityProperties();
        assertThatThrownBy(() -> properties.getTracing().setSamplingProbability(1.1d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
