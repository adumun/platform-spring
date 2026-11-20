package io.adumun.platform.spring.security.oidc.client;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Do not retain upstream access/refresh tokens after successful OIDC login.
 * Does NOT replace the OIDC principal or ID Token held in the session's SecurityContext;
 * consumers must explicitly replace those if an opaque local session is required.
 */
public final class NonPersistingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    public static final NonPersistingAuthorizedClientRepository INSTANCE =
            new NonPersistingAuthorizedClientRepository();

    private NonPersistingAuthorizedClientRepository() {}

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Never retain provider credentials in HTTP session or an authorized-client store.
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // No retained data exists.
    }
}
