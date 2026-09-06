package io.adumun.platform.spring.rest;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class GlobalRestExceptionHandler {
    @ExceptionHandler(RestFailureException.class)
    ResponseEntity<ApiEnvelope<Void>> handleKnown(RestFailureException exception) {
        RequestContext context = context();
        ApiError error = new ApiError(exception.code(), exception.category(), exception.retryable(), exception.publicMessage());
        return ResponseEntity.status(exception.status()).body(ApiEnvelope.failure(error, context));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception ignored) {
        RequestContext context = context();
        ApiError error = new ApiError("INTERNAL_ERROR", "INTERNAL", false, "Unexpected server error");
        return ResponseEntity.internalServerError().body(ApiEnvelope.failure(error, context));
    }

    private static RequestContext context() {
        return RequestContextHolder.current().orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            return new RequestContext(id, id);
        });
    }
}
