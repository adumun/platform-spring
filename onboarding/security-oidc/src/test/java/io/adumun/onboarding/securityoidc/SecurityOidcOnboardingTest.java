package io.adumun.onboarding.securityoidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityOidcOnboardingTest {
    @Autowired JwtDecoder jwtDecoder;
    @Autowired MockMvc mvc;

    @Test
    void oidcConfigurationCreatesDecoderWithoutConsumerWiring() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Test
    void securityCoreAndOidcAdapterComposeForProtectedEndpoints() throws Exception {
        mvc.perform(get("/secure")).andExpect(status().isUnauthorized());
    }
}
