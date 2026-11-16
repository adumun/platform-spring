package io.adumun.onboarding.oidclogin;

import io.adumun.platform.spring.security.oidc.client.AdumunOidcIdentityAcceptance;
import io.adumun.platform.spring.security.oidc.client.AdumunOidcLoginConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication
public class OidcLoginOnboardingApplication {
    public static void main(String[] args) {
        SpringApplication.run(OidcLoginOnboardingApplication.class, args);
    }

    @Bean
    ClientRegistrationRepository provider() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("external")
                .clientId("onboarding")
                .clientSecret("local-test-only")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("openid")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://issuer.example.test/authorize")
                .tokenUri("https://issuer.example.test/token")
                .issuerUri("https://issuer.example.test")
                .jwkSetUri("https://issuer.example.test/jwks")
                .build());
    }

    @Bean
    AdumunOidcIdentityAcceptance localAcceptance() {
        // Example only: production consumers must resolve a local identity here.
        return (request, user) -> {
            if (user.getSubject() == null || user.getSubject().isBlank()) {
                throw new IllegalArgumentException("Missing verified subject");
            }
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain login(HttpSecurity http, AdumunOidcLoginConfigurer oidc) throws Exception {
        http.securityMatcher("/oauth2/authorization/external", "/login/oauth2/code/external")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        oidc.configure(http);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain denyRest(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
    }
}
