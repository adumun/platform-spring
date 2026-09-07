package io.adumun.onboarding.securitycustom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityCustomDecoderOnboardingTest {
    @Autowired MockMvc mvc;

    @Test
    void publicPathIsAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/public")).andExpect(status().isOk());
    }

    @Test
    void protectedPathRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
    }

    @Test
    void applicationAuthorizationRuleRemainsConsumerOwned() throws Exception {
        mvc.perform(get("/admin").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());
    }
}
