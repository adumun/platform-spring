package io.adumun.platform.spring.security.oidc.client;

import java.util.Objects;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Delegates OIDC principal loading, then asks the consumer to accept the identity before
 * Spring Security completes authentication. Application failures fail closed.
 */
public final class AdumunOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final AdumunOidcIdentityAcceptance acceptance;

    public AdumunOidcUserService(
            OAuth2UserService<OidcUserRequest, OidcUser> delegate,
            AdumunOidcIdentityAcceptance acceptance) {
        this.delegate = Objects.requireNonNull(delegate);
        this.acceptance = Objects.requireNonNull(acceptance);
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        var user = Objects.requireNonNull(delegate.loadUser(request), "OIDC user must not be null");
        try {
            acceptance.accept(request, user);
            return user;
        } catch (RuntimeException failure) {
            // The external response does not include domain error details or secrets.
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("identity_rejected"), "Federated identity rejected");
        }
    }
}
