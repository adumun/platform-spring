package io.adumun.platform.spring.resilience;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AdumunResilienceProperties.class)
public class AdumunResilienceConfiguration {
    @Bean
    public AdumunResilienceRegistry adumunResilienceRegistry(AdumunResilienceProperties properties) {
        return new AdumunResilienceRegistry(properties);
    }
}
