package io.adumun.platform.spring.security.oidc;

import io.adumun.platform.spring.security.AdumunSecuritySupportConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@AutoConfiguration(before = AdumunSecuritySupportConfiguration.class)
@EnableConfigurationProperties(AdumunOidcSecurityProperties.class)
@ConditionalOnProperty(
        prefix = "adumun.security.identity.oidc",
        name = "enabled",
        havingValue = "true")
public class AdumunOidcSecurityAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder adumunOidcJwtDecoder(AdumunOidcSecurityProperties properties) {
        properties.validateEnabledConfiguration();
        NimbusJwtDecoder decoder = StringUtils.hasText(properties.getJwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build()
                : NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri()).build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> jwtValidator(AdumunOidcSecurityProperties properties) {
        properties.validateEnabledConfiguration();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.getIssuerUri()));
        properties.getAudiences().stream()
                .map(JwtAudienceValidator::new)
                .forEach(validators::add);
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
