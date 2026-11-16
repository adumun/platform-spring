package io.adumun.platform.spring.security.oidc.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Mechanism-only OIDC client. The consumer owns the security chains, session,
 * success/failure redirects and principal/identity policy.
 */
@AutoConfiguration
@EnableConfigurationProperties(AdumunOidcClientProperties.class)
@ConditionalOnProperty(prefix = "adumun.security.oidc-client", name = "enabled", havingValue = "true")
public class AdumunOidcClientAutoConfiguration {
    @Bean("adumunOidcAuthorizationRequestResolver")
    @ConditionalOnMissingBean(name = "adumunOidcAuthorizationRequestResolver")
    OAuth2AuthorizationRequestResolver adumunOidcAuthorizationRequestResolver(
            ClientRegistrationRepository registrations, AdumunOidcClientProperties properties) {
        var registration = registrations.findByRegistrationId(properties.getRegistrationId());
        properties.validate(registration);
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return new RestrictedOidcAuthorizationRequestResolver(resolver, properties.getRegistrationId());
    }

    @Bean("adumunOidcUserService")
    @ConditionalOnMissingBean(name = "adumunOidcUserService")
    OAuth2UserService<OidcUserRequest, OidcUser> adumunOidcUserService(
            AdumunOidcIdentityAcceptance acceptance) {
        return new AdumunOidcUserService(new OidcUserService(), acceptance);
    }

    @Bean
    @ConditionalOnMissingBean(AdumunOidcLoginConfigurer.class)
    AdumunOidcLoginConfigurer adumunOidcLoginConfigurer(
            @Qualifier("adumunOidcAuthorizationRequestResolver")
                    OAuth2AuthorizationRequestResolver requests,
            @Qualifier("adumunOidcUserService")
                    OAuth2UserService<OidcUserRequest, OidcUser> users) {
        return new AdumunOidcLoginConfigurer(requests, users);
    }
}
