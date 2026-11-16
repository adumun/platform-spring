package io.adumun.platform.spring.security.oidc.client;

import java.util.Objects;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Explicit composition into a consumer-owned SecurityFilterChain.
 * It intentionally does not create a filter chain or change CSRF/session defaults.
 */
public final class AdumunOidcLoginConfigurer {
    private final OAuth2AuthorizationRequestResolver authorizationRequests;
    private final OAuth2UserService<OidcUserRequest, OidcUser> users;

    public AdumunOidcLoginConfigurer(
            OAuth2AuthorizationRequestResolver authorizationRequests,
            OAuth2UserService<OidcUserRequest, OidcUser> users) {
        this.authorizationRequests = Objects.requireNonNull(authorizationRequests);
        this.users = Objects.requireNonNull(users);
    }

    /**
     * Identity-first default: never retain the upstream OAuth2AuthorizedClient after login.
     * The authenticated OIDC principal may still hold the ID Token in SecurityContext;
     * the product must replace it with its own local principal before retaining a session.
     */
    public void configure(HttpSecurity http) throws Exception {
        configure(http, NonPersistingAuthorizedClientRepository.INSTANCE);
    }

    /**
     * Consumers needing subsequent provider API calls can explicitly supply persistence.
     * Do not use this overload unless the lifecycle of those tokens is governed.
     */
    public void configure(HttpSecurity http, OAuth2AuthorizedClientRepository repository) throws Exception {
        Objects.requireNonNull(repository, "repository");
        http.oauth2Login(login -> login
                .authorizedClientRepository(repository)
                .authorizationEndpoint(authorization -> authorization
                        .authorizationRequestResolver(authorizationRequests))
                .userInfoEndpoint(info -> info.oidcUserService(users)));
    }
}
