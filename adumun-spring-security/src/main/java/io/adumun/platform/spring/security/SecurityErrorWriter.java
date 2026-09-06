package io.adumun.platform.spring.security;

import io.adumun.platform.spring.rest.ApiEnvelope;
import io.adumun.platform.spring.rest.ApiError;
import io.adumun.platform.spring.rest.RequestContext;
import io.adumun.platform.spring.rest.RequestContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

final class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, int status, String code, String category, String message) throws IOException {
        RequestContext context = RequestContextHolder.current().orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            return new RequestContext(id, id);
        });
        response.setStatus(status);
        response.setContentType("application/json");
        ApiError error = new ApiError(code, category, false, message);
        objectMapper.writeValue(response.getOutputStream(), ApiEnvelope.failure(error, context));
    }
}
