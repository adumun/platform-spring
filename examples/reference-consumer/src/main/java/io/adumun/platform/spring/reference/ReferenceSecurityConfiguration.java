package io.adumun.platform.spring.reference;

import io.adumun.platform.spring.security.AdumunAuthorizationRules;
import io.adumun.platform.spring.security.AdumunSecuritySupportConfiguration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Configuration(proxyBeanMethods = false)
@Import(AdumunSecuritySupportConfiguration.class)
class ReferenceSecurityConfiguration {
    @Bean
    AdumunAuthorizationRules referenceAuthorizationRules() {
        return authorize -> authorize.requestMatchers("/security/admin").hasAuthority("SCOPE_admin");
    }

    @Bean
    JwtDecoder referenceJwtDecoder() {
        return token -> {
            List<String> scopes = switch (token) {
                case "reference-user-token" -> List.of("user");
                case "reference-admin-token" -> List.of("user", "admin");
                default -> throw new JwtException("Invalid reference token");
            };
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("reference-subject")
                    .claim("scope", String.join(" ", scopes))
                    .issuedAt(now.minusSeconds(1))
                    .expiresAt(now.plusSeconds(300))
                    .build();
        };
    }
}
