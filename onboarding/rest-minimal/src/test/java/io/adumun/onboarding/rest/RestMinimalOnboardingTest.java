package io.adumun.onboarding.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RestMinimalOnboardingTest {
    @Autowired MockMvc mvc;

    @Test
    void dependencyAloneInstallsRequestContextInfrastructure() throws Exception {
        mvc.perform(get("/hello")
                        .header("X-Request-Id", "onb-rest-req")
                        .header("X-Correlation-Id", "onb-rest-corr"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "onb-rest-req"))
                .andExpect(header().string("X-Correlation-Id", "onb-rest-corr"));
    }
}
