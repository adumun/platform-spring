package io.adumun.platform.spring.security.oidc.client;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Ensures this configuration only starts the approved OIDC registration. */
final class RestrictedOidcAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final OAuth2AuthorizationRequestResolver delegate;
    private final String registrationId;
    private final PathPatternRequestMatcher authorizationPath;

    RestrictedOidcAuthorizationRequestResolver(
            OAuth2AuthorizationRequestResolver delegate, String registrationId) {
        this.delegate = Objects.requireNonNull(delegate);
        this.registrationId = Objects.requireNonNull(registrationId);
        this.authorizationPath = PathPatternRequestMatcher.withDefaults()
                .matcher("/oauth2/authorization/{registrationId}");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        var match = authorizationPath.matcher(request);
        if (!match.isMatch() || !registrationId.equals(match.getVariables().get("registrationId"))) {
            return null;
        }
        return delegate.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        if (!this.registrationId.equals(registrationId)) {
            return null;
        }
        return delegate.resolve(request, registrationId);
    }
}
