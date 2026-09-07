package io.adumun.onboarding.securityoidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityOidcOnboardingTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String REQUIRED_AUDIENCE = "onboarding-api";
    private static final String KEY_ID = "onboarding-key";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final RSAKey PUBLIC_JWK = new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
            .keyID(KEY_ID)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Autowired JwtDecoder jwtDecoder;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void oidcProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "adumun.security.identity.oidc.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void oidcConfigurationCreatesDecoderWithoutConsumerWiring() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    void signedTokenWithConfiguredIssuerAndAudienceIsAccepted() throws Exception {
        var jwt = jwtDecoder.decode(signedToken(List.of(REQUIRED_AUDIENCE)));

        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).contains(REQUIRED_AUDIENCE);
    }

    @Test
    void tokenWithoutConfiguredAudienceIsRejected() throws Exception {
        assertThatThrownBy(() -> jwtDecoder.decode(signedToken(List.of("different-api"))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void securityCoreAndOidcAdapterComposeForProtectedEndpoints() throws Exception {
        mvc.perform(get("/secure")).andExpect(status().isUnauthorized());
    }

    private static String signedToken(List<String> audiences) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("onboarding-subject")
                .audience(audiences)
                .issueTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        var token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims);
        token.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
        return token.serialize();
    }

    private static KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create onboarding RSA key", exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(PUBLIC_JWK).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to start onboarding JWKS server", exception);
        }
    }
}
