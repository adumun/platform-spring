package io.adumun.onboarding.securitycustom;

import io.adumun.platform.spring.security.AdumunAuthorizationRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SecurityCustomDecoderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityCustomDecoderApplication.class, args);
    }

    @GetMapping("/public")
    Map<String, String> publicEndpoint() {
        return Map.of("status", "public");
    }

    @GetMapping("/admin")
    Map<String, String> adminEndpoint() {
        return Map.of("status", "admin");
    }

    @Bean
    AdumunAuthorizationRules authorizationRules() {
        return authorize -> authorize.requestMatchers("/admin").hasAuthority("SCOPE_admin");
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            List<String> scopes = switch (token) {
                case "admin-token" -> List.of("admin");
                default -> throw new JwtException("Invalid onboarding token");
            };
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("onboarding-subject")
                    .claim("scope", String.join(" ", scopes))
                    .issuedAt(now.minusSeconds(1))
                    .expiresAt(now.plusSeconds(300))
                    .build();
        };
    }
}
