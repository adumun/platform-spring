package io.adumun.platform.spring.rest;

public record ApiError(String code, String category, boolean retryable, String message) {}
