package io.adumun.platform.spring.rest;

public class RestFailureException extends RuntimeException {
    private final int status;
    private final String code;
    private final String category;
    private final boolean retryable;
    private final String publicMessage;

    public RestFailureException(int status, String code, String category, boolean retryable, String publicMessage) {
        super(publicMessage);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be an HTTP error status");
        }
        this.status = status;
        this.code = code;
        this.category = category;
        this.retryable = retryable;
        this.publicMessage = publicMessage;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String category() { return category; }
    public boolean retryable() { return retryable; }
    public String publicMessage() { return publicMessage; }
}
