package io.adumun.platform.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

public final class AdumunCorsConfigurationSource implements CorsConfigurationSource {
    private final AdumunSecurityProperties.Cors properties;

    public AdumunCorsConfigurationSource(AdumunSecurityProperties properties) {
        this.properties = properties.getCors();
        validate();
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return null;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(properties.getAllowedOrigins()));
        configuration.setAllowedMethods(List.copyOf(properties.getAllowedMethods()));
        configuration.setAllowedHeaders(List.copyOf(properties.getAllowedHeaders()));
        configuration.setExposedHeaders(List.copyOf(properties.getExposedHeaders()));
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(properties.getMaxAge());
        return configuration;
    }

    private void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getAllowedOrigins().isEmpty()) {
            throw new IllegalStateException("adumun.security.cors.allowed-origins is required when CORS is enabled");
        }
        if (properties.isAllowCredentials() && properties.getAllowedOrigins().contains("*")) {
            throw new IllegalStateException("CORS wildcard origin cannot be combined with credentials");
        }
    }
}
