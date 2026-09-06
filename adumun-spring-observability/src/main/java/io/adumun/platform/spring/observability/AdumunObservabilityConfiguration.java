package io.adumun.platform.spring.observability;

import io.micrometer.common.KeyValues;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdumunObservabilityProperties.class)
public class AdumunObservabilityConfiguration {
    @Bean
    public AdumunObservationTags adumunObservationTags(AdumunObservabilityProperties properties) {
        return new AdumunObservationTags(properties);
    }

    public static final class AdumunObservationTags {
        private final AdumunObservabilityProperties properties;

        public AdumunObservationTags(AdumunObservabilityProperties properties) {
            this.properties = properties;
        }

        public KeyValues commonTags() {
            return KeyValues.of(
                    "service.name", properties.getServiceName(),
                    "deployment.environment", properties.getEnvironment(),
                    "service.version", properties.getBuildVersion());
        }
    }
}
