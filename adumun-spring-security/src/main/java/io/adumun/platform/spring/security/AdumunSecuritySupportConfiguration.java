package io.adumun.platform.spring.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class AdumunSecuritySupportConfiguration {
    @Bean(name = "adumunAuthenticationEntryPoint")
    public AdumunAuthenticationEntryPoint adumunAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new AdumunAuthenticationEntryPoint(objectMapper);
    }

    @Bean(name = "adumunAccessDeniedHandler")
    public AdumunAccessDeniedHandler adumunAccessDeniedHandler(ObjectMapper objectMapper) {
        return new AdumunAccessDeniedHandler(objectMapper);
    }
}
