package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiClientException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public ForbiddenException() {
        this("Access denied");
    }
}
