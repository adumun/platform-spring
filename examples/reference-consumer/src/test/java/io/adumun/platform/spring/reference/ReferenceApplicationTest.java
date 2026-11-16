package io.adumun.platform.spring.reference;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReferenceApplicationTest {
    @Autowired MockMvc mvc;

    @Test
    void sharedEnvelopeAndIdentityAreWired() throws Exception {
        mvc.perform(get("/demo").header("X-Request-Id", "req-1").header("X-Correlation-Id", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-1"))
                .andExpect(header().string("X-Correlation-Id", "corr-1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.correlationId").value("corr-1"));
    }

    @Test
    void readinessAndLivenessAreDistinctRoutes() throws Exception {
        mvc.perform(get("/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void dependencyFailurePreservesCategoryAndRetryability() throws Exception {
        mvc.perform(get("/demo/failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.category").value("DEPENDENCY"))
                .andExpect(jsonPath("$.error.retryable").value(true));
    }

    @Test
    void unexpectedFailureDoesNotLeakInternalDetail() throws Exception {
        mvc.perform(get("/demo/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(jsonPath("$.error.message").value("Unexpected server error"));
    }
}
