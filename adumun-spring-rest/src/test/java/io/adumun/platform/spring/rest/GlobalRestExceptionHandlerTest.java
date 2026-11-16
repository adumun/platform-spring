package io.adumun.platform.spring.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalRestExceptionHandlerTest {
    private final GlobalRestExceptionHandler handler = new GlobalRestExceptionHandler();

    @Test
    void unexpectedErrorsDoNotLeakInternalMessages() {
        var response = handler.handleUnexpected(new IllegalStateException("database password leaked"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Unexpected server error");
        assertThat(response.getBody().toString()).doesNotContain("database password leaked");
    }

    @Test
    void knownFailuresPreservePublicSemantics() {
        var response = handler.handleKnown(new RestFailureException(409, "CONFLICT", "BUSINESS", false, "Conflict"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo(new ApiError("CONFLICT", "BUSINESS", false, "Conflict"));
    }

    @Test
    void invalidRequestBodyReturnsBadRequestWithSafeEnvelope() throws Exception {
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            MockMvc mockMvc = standaloneSetup(new ValidationController())
                    .setControllerAdvice(handler)
                    .setValidator(validator)
                    .build();

            mockMvc.perform(post("/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.error.category").value("CLIENT"))
                    .andExpect(jsonPath("$.error.message").value("Request could not be processed"));
        }
    }

    @RestController
    static class ValidationController {
        @PostMapping("/validation")
        ResponseEntity<Void> validate(@Valid @RequestBody ValidationRequest request) {
            return ResponseEntity.noContent().build();
        }
    }

    record ValidationRequest(@NotBlank String name) {}
}
