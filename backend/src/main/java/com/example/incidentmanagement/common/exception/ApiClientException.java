package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class ApiClientException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiClientException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
