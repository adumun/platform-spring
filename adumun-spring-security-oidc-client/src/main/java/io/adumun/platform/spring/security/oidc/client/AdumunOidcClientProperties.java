package io.adumun.platform.spring.security.oidc.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/** Protocol guardrails for one confidential OIDC login registration. */
@ConfigurationProperties(prefix = "adumun.security.oidc-client")
public class AdumunOidcClientProperties {
    private boolean enabled;
    private boolean allowInsecureLoopback;
    private String registrationId;
    private List<String> allowedScopes = new ArrayList<>(List.of("openid"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowInsecureLoopback() {
        return allowInsecureLoopback;
    }

    public void setAllowInsecureLoopback(boolean allowInsecureLoopback) {
        this.allowInsecureLoopback = allowInsecureLoopback;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes == null ? new ArrayList<>() : new ArrayList<>(allowedScopes);
    }

    void validate(ClientRegistration registration) {
        if (!StringUtils.hasText(registrationId) || !registrationId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("A valid adumun.security.oidc-client.registration-id is required");
        }
        if (registration == null) {
            throw new IllegalStateException("OIDC client registration was not found: " + registrationId);
        }
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(registration.getAuthorizationGrantType())) {
            throw new IllegalStateException("OIDC login requires authorization_code");
        }
        if (!StringUtils.hasText(registration.getClientId())
                || !StringUtils.hasText(registration.getClientSecret())
                || ClientAuthenticationMethod.NONE.equals(registration.getClientAuthenticationMethod())) {
            throw new IllegalStateException("OIDC login requires a confidential server-side client");
        }
        if (!StringUtils.hasText(registration.getRedirectUri())) {
            throw new IllegalStateException("OIDC client requires redirect URI");
        }
        requireTrustedUri("issuer", registration.getProviderDetails().getIssuerUri());
        requireTrustedUri("authorization", registration.getProviderDetails().getAuthorizationUri());
        requireTrustedUri("token", registration.getProviderDetails().getTokenUri());
        requireTrustedUri("JWKS", registration.getProviderDetails().getJwkSetUri());
        Set<String> scopes = new LinkedHashSet<>(allowedScopes);
        if (!scopes.contains("openid") || scopes.size() != allowedScopes.size()
                || allowedScopes.stream().anyMatch(scope -> !StringUtils.hasText(scope))
                || !scopes.equals(registration.getScopes())) {
            throw new IllegalStateException(
                    "OIDC client scopes must exactly match adumun.security.oidc-client.allowed-scopes");
        }
    }

    private void requireTrustedUri(String label, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("OIDC client requires a trusted " + label + " URI");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("OIDC client has an invalid " + label + " URI");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("OIDC client has an invalid " + label + " URI");
        }
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
        if (!secure && !(allowInsecureLoopback && local && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("OIDC client requires HTTPS for " + label + " URI");
        }
    }
}
