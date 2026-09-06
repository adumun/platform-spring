package io.adumun.platform.spring.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdumunSecurityProperties.class)
public class AdumunSecuritySupportConfiguration {
    @Bean(name = "adumunAuthenticationEntryPoint")
    public AdumunAuthenticationEntryPoint adumunAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new AdumunAuthenticationEntryPoint(objectMapper);
    }

    @Bean(name = "adumunAccessDeniedHandler")
    public AdumunAccessDeniedHandler adumunAccessDeniedHandler(ObjectMapper objectMapper) {
        return new AdumunAccessDeniedHandler(objectMapper);
    }

    @Bean(name = "adumunCorsConfigurationSource")
    public CorsConfigurationSource adumunCorsConfigurationSource(AdumunSecurityProperties properties) {
        return new AdumunCorsConfigurationSource(properties);
    }

    @Bean(name = "adumunSecurityFilterChain")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain adumunSecurityFilterChain(
            HttpSecurity http,
            AdumunAuthenticationEntryPoint authenticationEntryPoint,
            AdumunAccessDeniedHandler accessDeniedHandler,
            @Qualifier("adumunCorsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
            AdumunSecurityProperties properties,
            ObjectProvider<AdumunAuthorizationRules> rules) throws Exception {
        AdumunStatelessBearerSecurity.configure(
                http, authenticationEntryPoint, accessDeniedHandler, corsConfigurationSource);

        http.authorizeHttpRequests(authorize -> {
            if (!properties.getPublicPaths().isEmpty()) {
                authorize.requestMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll();
            }
            rules.orderedStream().forEach(rule -> rule.configure(authorize));
            authorize.anyRequest().authenticated();
        });
        return http.build();
    }
}
