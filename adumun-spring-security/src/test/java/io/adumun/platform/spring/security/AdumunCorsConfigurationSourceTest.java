package io.adumun.platform.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class AdumunCorsConfigurationSourceTest {
    @Test
    void corsIsDisabledByDefault() {
        AdumunSecurityProperties properties = new AdumunSecurityProperties();
        AdumunCorsConfigurationSource source = new AdumunCorsConfigurationSource(properties);

        assertThat(source.getCorsConfiguration(new MockHttpServletRequest())).isNull();
    }

    @Test
    void enabledCorsUsesConsumerAllowlist() {
        AdumunSecurityProperties properties = new AdumunSecurityProperties();
        properties.getCors().setEnabled(true);
        properties.getCors().setAllowedOrigins(List.of("https://app.example.test"));
        properties.getCors().setAllowCredentials(true);

        AdumunCorsConfigurationSource source = new AdumunCorsConfigurationSource(properties);
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.test");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getExposedHeaders()).contains("X-Request-Id", "X-Correlation-Id");
    }

    @Test
    void enabledCorsRequiresExplicitOrigins() {
        AdumunSecurityProperties properties = new AdumunSecurityProperties();
        properties.getCors().setEnabled(true);

        assertThatThrownBy(() -> new AdumunCorsConfigurationSource(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allowed-origins");
    }

    @Test
    void credentialedCorsRejectsWildcardOrigin() {
        AdumunSecurityProperties properties = new AdumunSecurityProperties();
        properties.getCors().setEnabled(true);
        properties.getCors().setAllowedOrigins(List.of("*"));
        properties.getCors().setAllowCredentials(true);

        assertThatThrownBy(() -> new AdumunCorsConfigurationSource(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }
}
