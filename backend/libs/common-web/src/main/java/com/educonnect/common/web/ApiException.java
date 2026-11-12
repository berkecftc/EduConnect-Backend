package com.educonnect.common.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final HttpHeaders headers;

    public ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, HttpHeaders.EMPTY);
    }

    public ApiException(HttpStatus status, String errorCode, String message, HttpHeaders headers) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
