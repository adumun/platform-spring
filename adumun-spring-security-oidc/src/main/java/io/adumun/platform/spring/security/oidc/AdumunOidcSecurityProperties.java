package io.adumun.platform.spring.security.oidc;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "adumun.security.identity.oidc")
public class AdumunOidcSecurityProperties {
    private boolean enabled;
    private String issuerUri;
    private String jwkSetUri;
    private List<String> audiences = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences == null ? new ArrayList<>() : new ArrayList<>(audiences);
    }

    void validateEnabledConfiguration() {
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalStateException(
                    "adumun.security.identity.oidc.issuer-uri is required when the OIDC adapter is enabled");
        }
        if (audiences.isEmpty()) {
            throw new IllegalStateException(
                    "adumun.security.identity.oidc.audiences must contain at least one value when the OIDC adapter is enabled");
        }
        if (audiences.stream().anyMatch(audience -> !StringUtils.hasText(audience))) {
            throw new IllegalStateException(
                    "adumun.security.identity.oidc.audiences must not contain blank values");
        }
    }
}
