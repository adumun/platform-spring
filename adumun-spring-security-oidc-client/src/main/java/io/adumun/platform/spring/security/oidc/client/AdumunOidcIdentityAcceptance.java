package io.adumun.platform.spring.security.oidc.client;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Product-specific admission hook. Invoked only after Spring Security validates the upstream
 * ID Token; it must deny revoked/suspended identities by throwing an exception.
 * Do not log tokens, subject or client secrets.
 */
@FunctionalInterface
public interface AdumunOidcIdentityAcceptance {
    void accept(OidcUserRequest request, OidcUser user);
}
