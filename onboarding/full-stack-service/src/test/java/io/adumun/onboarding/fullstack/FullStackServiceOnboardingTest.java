package io.adumun.onboarding.fullstack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.adumun.platform.spring.observability.AdumunObservabilityConfiguration.AdumunObservationTags;
import io.adumun.platform.spring.resilience.AdumunResilienceConfiguration.AdumunResilienceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FullStackServiceOnboardingTest {
    @Autowired MockMvc mvc;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired AdumunObservationTags tags;
    @Autowired AdumunResilienceRegistry resilience;

    @Test
    void allPlatformCapabilitiesComposeFromDependenciesAndYaml() throws Exception {
        assertThat(jwtDecoder).isNotNull();
        assertThat(tags.commonTags().stream().map(kv -> kv.getKey() + "=" + kv.getValue()).toList())
                .contains("service.name=onboarding-full-stack");
        assertThat(resilience.retries().getAllRetries())
                .extracting(retry -> retry.getName())
                .contains("catalog");

        mvc.perform(get("/public")
                        .header("X-Request-Id", "onb-full-req")
                        .header("X-Correlation-Id", "onb-full-corr"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "onb-full-req"))
                .andExpect(header().string("X-Correlation-Id", "onb-full-corr"));

        mvc.perform(get("/admin")).andExpect(status().isUnauthorized());
    }
}
