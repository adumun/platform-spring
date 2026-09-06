package io.adumun.platform.spring.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class AdumunAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityErrorWriter writer;

    public AdumunAccessDeniedHandler(ObjectMapper objectMapper) {
        this.writer = new SecurityErrorWriter(objectMapper);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        writer.write(response, HttpServletResponse.SC_FORBIDDEN,
                "AUTHORIZATION_DENIED", "AUTHORIZATION", "Access denied");
    }
}
