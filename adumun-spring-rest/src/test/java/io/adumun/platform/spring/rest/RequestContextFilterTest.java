package io.adumun.platform.spring.rest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {
    @Test
    void propagatesExplicitRequestAndCorrelationIds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextFilter.REQUEST_ID_HEADER, "req-123");
        request.addHeader(RequestContextFilter.CORRELATION_ID_HEADER, "corr-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<RequestContext> captured = new AtomicReference<>();

        new RequestContextFilter().doFilter(request, response, (req, res) -> captured.set(RequestContextHolder.current().orElseThrow()));

        assertThat(captured.get()).isEqualTo(new RequestContext("req-123", "corr-456"));
        assertThat(response.getHeader(RequestContextFilter.REQUEST_ID_HEADER)).isEqualTo("req-123");
        assertThat(response.getHeader(RequestContextFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-456");
        assertThat(RequestContextHolder.current()).isEmpty();
    }
}
