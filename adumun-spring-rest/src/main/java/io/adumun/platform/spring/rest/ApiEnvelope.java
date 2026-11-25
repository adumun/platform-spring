package io.adumun.platform.spring.rest;

public record ApiEnvelope<T>(boolean success, T data, ApiError error, String requestId, String correlationId) {
    public static <T> ApiEnvelope<T> success(T data, RequestContext context) {
        return new ApiEnvelope<>(true, data, null, context.requestId(), context.correlationId());
    }

    public static <T> ApiEnvelope<T> failure(ApiError error, RequestContext context) {
        return new ApiEnvelope<>(false, null, error, context.requestId(), context.correlationId());
    }
}
