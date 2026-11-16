package io.adumun.platform.spring.observability;

import io.adumun.platform.spring.rest.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public final class AdumunLoggingContextFilter extends OncePerRequestFilter {
    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_CORRELATION_ID = "correlation_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        var context = RequestContextHolder.current();
        context.ifPresent(value -> {
            MDC.put(MDC_REQUEST_ID, value.requestId());
            MDC.put(MDC_CORRELATION_ID, value.correlationId());
        });
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
