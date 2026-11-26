package io.adumun.platform.spring.security.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class AdumunOidcClientAutoConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdumunOidcClientAutoConfiguration.class))
            .withBean(ClientRegistrationRepository.class,
                    () -> new InMemoryClientRegistrationRepository(oidcRegistration("openid")))
            .withBean(AdumunOidcIdentityAcceptance.class, () -> (request, user) -> {});

    @Test
    void disabledByDefaultDoesNotCreateProtocolBeans() {
        context.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(AdumunOidcLoginConfigurer.class);
            assertThat(ctx).doesNotHaveBean(OAuth2AuthorizationRequestResolver.class);
        });
    }

    @Test
    void enabledCreatesConfigurerAndForcesPkceS256ForConfidentialClient() {
        context.withPropertyValues("adumun.security.oidc-client.enabled=true",
                        "adumun.security.oidc-client.registration-id=external")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(AdumunOidcLoginConfigurer.class);
                    var resolver = ctx.getBean(
                            "adumunOidcAuthorizationRequestResolver", OAuth2AuthorizationRequestResolver.class);
                    var request = new MockHttpServletRequest("GET", "/oauth2/authorization/external");
                    request.setServletPath("/oauth2/authorization/external");
                    var auth = resolver.resolve(request);
                    assertThat(auth).isNotNull();
                    assertThat(auth.getAdditionalParameters())
                            .containsEntry("code_challenge_method", "S256")
                            .containsKey("code_challenge");
                    assertThat(auth.getAttributes()).containsKey("code_verifier");
                    assertThat(auth.getAdditionalParameters()).containsKey("nonce");
                    var second = resolver.resolve(request);
                    assertThat(second).isNotNull();
                    assertThat(second.getState()).isNotEqualTo(auth.getState());
                    assertThat(second.getAdditionalParameters().get("nonce"))
                            .isNotEqualTo(auth.getAdditionalParameters().get("nonce"));
                    assertThat(second.getAttributes().get("code_verifier"))
                            .isNotEqualTo(auth.getAttributes().get("code_verifier"));
                    var verifier = (String) auth.getAttributes().get("code_verifier");
                    var digest = MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
                    var expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
                    assertThat(auth.getAdditionalParameters())
                            .containsEntry("code_challenge", expectedChallenge);
                    assertThat(auth.getAuthorizationRequestUri()).doesNotContain(verifier);
                    var rootMapped = new MockHttpServletRequest("GET", "/oauth2/authorization/external");
                    assertThat(rootMapped.getServletPath()).isEmpty();
                    assertThat(resolver.resolve(rootMapped)).isNotNull();
                    var contextual = new MockHttpServletRequest("GET", "/app/oauth2/authorization/external");
                    contextual.setContextPath("/app");
                    assertThat(resolver.resolve(contextual)).isNotNull();
                    var wrong = new MockHttpServletRequest("GET", "/oauth2/authorization/other");
                    assertThat(resolver.resolve(wrong)).isNull();
                    var other = new MockHttpServletRequest("GET", "/oauth2/authorization/other");
                    other.setServletPath("/oauth2/authorization/other");
                    assertThat(resolver.resolve(other)).isNull();
                });
    }

    @Test
    void enabledWithoutRegistrationIdFailsClosed() {
        context.withPropertyValues("adumun.security.oidc-client.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void unexpectedScopesAreRejected() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AdumunOidcClientAutoConfiguration.class))
                .withBean(ClientRegistrationRepository.class,
                        () -> new InMemoryClientRegistrationRepository(oidcRegistration("openid", "email")))
                .withBean(AdumunOidcIdentityAcceptance.class, () -> (request, user) -> {})
                .withPropertyValues("adumun.security.oidc-client.enabled=true",
                        "adumun.security.oidc-client.registration-id=external")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsUntrustedNonLocalProviderEndpoints() {
        var properties = new AdumunOidcClientProperties();
        properties.setRegistrationId("external");
        var invalid = ClientRegistration.withClientRegistration(oidcRegistration("openid"))
                .tokenUri("http://attacker.example/token").build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> properties.validate(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void allowsExplicitLoopbackOnlyForLocalIdpSimulation() {
        var properties = new AdumunOidcClientProperties();
        properties.setRegistrationId("external");
        properties.setAllowInsecureLoopback(true);
        var local = ClientRegistration.withClientRegistration(oidcRegistration("openid"))
                .authorizationUri("http://127.0.0.1:8081/authorize")
                .tokenUri("http://127.0.0.1:8081/token")
                .issuerUri("http://127.0.0.1:8081")
                .jwkSetUri("http://127.0.0.1:8081/jwks").build();
        properties.validate(local);
    }

    @Test
    void missingConsumerAcceptanceFailsStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AdumunOidcClientAutoConfiguration.class))
                .withBean(ClientRegistrationRepository.class,
                        () -> new InMemoryClientRegistrationRepository(oidcRegistration("openid")))
                .withPropertyValues("adumun.security.oidc-client.enabled=true",
                        "adumun.security.oidc-client.registration-id=external")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    private static ClientRegistration oidcRegistration(String... scopes) {
        return ClientRegistration.withRegistrationId("external")
                .clientId("test-client")
                .clientSecret("test-only-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .authorizationUri("https://issuer.example.test/authorize")
                .tokenUri("https://issuer.example.test/token")
                .issuerUri("https://issuer.example.test")
                .jwkSetUri("https://issuer.example.test/jwks")
                .build();
    }
}
