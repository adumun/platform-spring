package io.adumun.platform.spring.reference;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityReferenceApplicationTest {
    @Autowired MockMvc mvc;

    @Test
    void publicEndpointDoesNotRequireAuthentication() throws Exception {
        mvc.perform(get("/security/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("public"));
    }

    @Test
    void protectedEndpointReturnsStandard401AndPreservesCorrelation() throws Exception {
        mvc.perform(get("/security/authenticated")
                        .header("X-Request-Id", "req-security-401")
                        .header("X-Correlation-Id", "corr-security-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "req-security-401"))
                .andExpect(header().string("X-Correlation-Id", "corr-security-401"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.error.category").value("AUTHENTICATION"))
                .andExpect(jsonPath("$.requestId").value("req-security-401"))
                .andExpect(jsonPath("$.correlationId").value("corr-security-401"));
    }

    @Test
    void insufficientAuthorityReturnsStandard403() throws Exception {
        mvc.perform(get("/security/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer reference-user-token")
                        .header("X-Request-Id", "req-security-403")
                        .header("X-Correlation-Id", "corr-security-403"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHORIZATION_DENIED"))
                .andExpect(jsonPath("$.error.category").value("AUTHORIZATION"))
                .andExpect(jsonPath("$.requestId").value("req-security-403"))
                .andExpect(jsonPath("$.correlationId").value("corr-security-403"));
    }

    @Test
    void authenticatedAndAuthorizedRequestsSucceed() throws Exception {
        mvc.perform(get("/security/authenticated")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer reference-user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("reference-subject"));

        mvc.perform(get("/security/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer reference-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("admin"));
    }
}
