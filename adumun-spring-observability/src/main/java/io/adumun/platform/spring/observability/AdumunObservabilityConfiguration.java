package io.adumun.platform.spring.observability;

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
}
