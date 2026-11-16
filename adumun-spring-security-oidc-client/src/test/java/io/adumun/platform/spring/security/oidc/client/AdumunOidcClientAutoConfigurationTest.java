package io.adumun.platform.spring.security.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;

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
