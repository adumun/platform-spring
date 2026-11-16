package io.adumun.platform.spring.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

public final class AdumunAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final SecurityErrorWriter writer;

    public AdumunAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.writer = new SecurityErrorWriter(objectMapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "AUTHENTICATION", "Authentication required");
    }
}
