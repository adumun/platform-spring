package io.adumun.platform.spring.rest;

import java.util.Optional;

public final class RequestContextHolder {
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private RequestContextHolder() {}

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(RequestContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }
}
