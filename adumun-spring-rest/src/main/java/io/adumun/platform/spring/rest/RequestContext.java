package io.adumun.platform.spring.rest;

import java.util.Objects;

public record RequestContext(String requestId, String correlationId) {
    public RequestContext {
        requestId = requireText(requestId, "requestId");
        correlationId = requireText(correlationId, "correlationId");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
