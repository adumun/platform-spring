package io.adumun.platform.spring.rest;

import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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
    ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            return handleClientError(errorResponse.getStatusCode());
        }

        RequestContext context = context();
        ApiError error = new ApiError("INTERNAL_ERROR", "INTERNAL", false, "Unexpected server error");
        return ResponseEntity.internalServerError().body(ApiEnvelope.failure(error, context));
    }

    private ResponseEntity<ApiEnvelope<Void>> handleClientError(HttpStatusCode status) {
        RequestContext context = context();
        String code = status.value() == 400 ? "INVALID_REQUEST" : "HTTP_REQUEST_ERROR";
        ApiError error = new ApiError(code, "CLIENT", false, "Request could not be processed");
        return ResponseEntity.status(status).body(ApiEnvelope.failure(error, context));
    }

    private static RequestContext context() {
        return RequestContextHolder.current().orElseGet(() -> {
            String id = UUID.randomUUID().toString();
            return new RequestContext(id, id);
        });
    }
}
