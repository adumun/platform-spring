package io.adumun.platform.spring.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AdumunOidcSecurityAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdumunOidcSecurityAutoConfiguration.class));

    @Test
    void adapterIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
    }

    @Test
    void enabledAdapterRequiresIssuer() {
        contextRunner.withPropertyValues("adumun.security.identity.oidc.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("issuer-uri is required");
                });
    }

    @Test
    void consumerJwtDecoderTakesPrecedence() {
        contextRunner
                .withUserConfiguration(ConsumerDecoderConfiguration.class)
                .withPropertyValues("adumun.security.identity.oidc.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context.getBean(JwtDecoder.class)).isSameAs(context.getBean("consumerJwtDecoder"));
                });
    }

    @Test
    void audienceValidatorRequiresEveryConfiguredAudience() {
        var properties = new AdumunOidcSecurityProperties();
        properties.setIssuerUri("https://issuer.example");
        properties.setAudiences(List.of("orders", "internal-api"));
        var validator = AdumunOidcSecurityAutoConfiguration.jwtValidator(properties);

        assertThat(validator.validate(jwt(List.of("orders", "internal-api"))).hasErrors()).isFalse();
        assertThat(validator.validate(jwt(List.of("orders"))).hasErrors()).isTrue();
    }

    private static Jwt jwt(List<String> audiences) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://issuer.example")
                .audience(audiences)
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerDecoderConfiguration {
        @Bean
        JwtDecoder consumerJwtDecoder() {
            return token -> jwt(List.of("orders"));
        }
    }
}
