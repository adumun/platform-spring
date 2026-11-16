package io.adumun.onboarding.oidclogin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.adumun.platform.spring.security.oidc.client.AdumunOidcLoginConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OidcLoginOnboardingTest {
    @Autowired MockMvc mvc;
    @Autowired AdumunOidcLoginConfigurer oidc;

    @Test
    void consumerResolvesCompiledModuleAndKeepsItsOwnSecurityChains() throws Exception {
        assertThat(oidc).isNotNull();
        var result = mvc.perform(get("/oauth2/authorization/external"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("https://issuer.example.test/authorize")
                .contains("code_challenge_method=S256")
                .contains("state=")
                .contains("nonce=");
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void unconfiguredPathsDoNotEscapeTheConsumerFallback() throws Exception {
        var result = mvc.perform(get("/unconfigured")).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }
}
