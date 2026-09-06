package io.adumun.platform.spring.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
