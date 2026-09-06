package io.adumun.platform.spring.observability;

import io.micrometer.common.KeyValues;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(AdumunObservabilityProperties.class)
public class AdumunObservabilityConfiguration {
    @Bean
    public AdumunObservationTags adumunObservationTags(AdumunObservabilityProperties properties) {
        return new AdumunObservationTags(properties);
    }

    @Bean
    public FilterRegistrationBean<AdumunLoggingContextFilter> adumunLoggingContextFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new AdumunLoggingContextFilter());
        registration.setName("adumunLoggingContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
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
