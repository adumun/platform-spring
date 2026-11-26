package io.adumun.onboarding.oidclogin;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "adumun.security.oidc-client.allow-insecure-loopback=true",
        classes = {OidcLoginOnboardingApplication.class, OidcProtocolTest.ProviderConfig.class})
@AutoConfigureMockMvc
class OidcProtocolTest {
    @Autowired MockMvc mvc;
    @Autowired Provider provider;

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfig {
        @Bean(destroyMethod = "close") Provider simulatedProvider() throws Exception { return new Provider(); }
        @Bean @Primary ClientRegistrationRepository simulatedRegistration(Provider p) {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("external")
                    .clientId("onboarding").clientSecret("local-test-only")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .scope("openid").redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .issuerUri(p.issuer).authorizationUri(p.issuer + "/authorize")
                    .tokenUri(p.issuer + "/token").jwkSetUri(p.issuer + "/jwks").build());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "state", "nonce", "issuer", "audience", "expired", "signature", "pkce", "azp", "missing-azp", "multi-audience", "foreign-session"})
    void callbackValidatesProtocol(String scenario) throws Exception {
        var start = mvc.perform(get("/oauth2/authorization/external"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) start.getRequest().getSession(false);
        var params = parse(URI.create(start.getResponse().getHeader("Location")).getRawQuery());
        var code = UUID.randomUUID().toString();
        provider.codes.put(code, new Grant(params.get("nonce"), scenario.equals("pkce") ? "tampered-challenge" : params.get("code_challenge"), scenario));
        var originalSessionId = session.getId();
        if (scenario.equals("foreign-session")) session = new MockHttpSession();
        // Model a browser returning the existing session cookie, including Servlet metadata.
        var requestedSessionId = session.getId();
        var callback = mvc.perform(get("/login/oauth2/code/external").session(session)
                .with(request -> {
                    request.setRequestedSessionId(requestedSessionId);
                    request.setRequestedSessionIdValid(true);
                    request.setRequestedSessionIdFromCookie(true);
                    return request;
                })
                .param("code", code).param("state", scenario.equals("state") ? "wrong" : params.get("state")))
                .andExpect(status().is3xxRedirection()).andReturn();
        var authenticated = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (scenario.equals("valid") || scenario.equals("multi-audience")) {
            assertThat(authenticated).isNotNull();
            assertThat(session.getId()).isNotEqualTo(originalSessionId);
            assertThat(Collections.list(session.getAttributeNames()))
                    .noneMatch(name -> name.contains("AUTHORIZED_CLIENT"));
            assertThat(authenticated.getAuthentication().isAuthenticated()).isTrue();
            assertThat(authenticated.getAuthentication().getName()).isEqualTo("verified-subject");
            var request = callback.getRequest();
            var principal = authenticated.getAuthentication();
            org.springframework.security.oauth2.client.OAuth2AuthorizedClient storedClient =
                    new org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository()
                            .loadAuthorizedClient("external", principal, request);
            assertThat(storedClient).isNull();
            assertThat(provider.codes).doesNotContainKey(code);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/oauth2/authorization/external").session(session))
                    .andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/logout").session(session))
                    .andExpect(status().isForbidden());
            assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                    .isNotNull();
            var replay = mvc.perform(get("/oauth2/authorization/external")).andReturn();
            var fresh = (MockHttpSession) replay.getRequest().getSession(false);
            var freshParams = parse(URI.create(replay.getResponse().getHeader("Location")).getRawQuery());
            mvc.perform(get("/login/oauth2/code/external").session(fresh)
                    .param("code", code).param("state", freshParams.get("state")))
                    .andExpect(status().is3xxRedirection());
            assertThat(fresh.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        } else {
            assertThat(authenticated).isNull();
            assertThat(callback.getResponse().getHeader("Location")).contains("error");
        }
    }

    record Grant(String nonce, String challenge, String scenario) {}
    static Map<String,String> parse(String query) {
        var result = new HashMap<String,String>();
        for (String pair : query.split("&")) {
            var parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }

    static final class Provider implements AutoCloseable {
        final RSAKey key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        final RSAKey wrongKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        final Map<String,Grant> codes = new ConcurrentHashMap<>();
        Provider() throws Exception {
            server.createContext("/jwks", e -> respond(e, 200, "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}"));
            server.createContext("/token", e -> {
                try {
                    var params = parse(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var grant = codes.remove(params.getOrDefault("code", ""));
                    var verifier = params.getOrDefault("code_verifier", "");
                    var challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                    if (grant == null || verifier.isBlank() || !challenge.equals(grant.challenge())) {
                        respond(e, 400, "{\"error\":\"invalid_grant\"}"); return;
                    }
                    var now = Instant.now();
                    var builder = new JWTClaimsSet.Builder().subject("verified-subject")
                            .issuer(grant.scenario().equals("issuer") ? issuer + "/wrong" : issuer)
                            .audience(grant.scenario().equals("audience") ? List.of("wrong-client") :
                                    Set.of("azp", "missing-azp", "multi-audience").contains(grant.scenario())
                                            ? List.of("onboarding", "another-client") : List.of("onboarding"))
                            .issueTime(Date.from(now.minusSeconds(600)))
                            .expirationTime(Date.from(now.plusSeconds(grant.scenario().equals("expired") ? -300 : 300)))
                            .claim("nonce", grant.scenario().equals("nonce") ? "wrong" : grant.nonce());
                    if (grant.scenario().equals("azp")) builder.claim("azp", "wrong-client");
                    if (grant.scenario().equals("multi-audience")) builder.claim("azp", "onboarding");
                    var claims = builder.build();
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
                    jwt.sign(new RSASSASigner(grant.scenario().equals("signature") ? wrongKey : key));
                    respond(e, 200, "{\"access_token\":\"test-access\",\"token_type\":\"Bearer\",\"expires_in\":300,\"id_token\":\"" + jwt.serialize() + "\"}");
                } catch (Exception ex) { respond(e, 500, "{\"error\":\"server_error\"}"); }
            });
            server.start();
        }
        static void respond(com.sun.net.httpserver.HttpExchange e, int status, String json) throws java.io.IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(status, bytes.length);
            try (var out = e.getResponseBody()) { out.write(bytes); }
        }
        public void close() { server.stop(0); }
    }
}
