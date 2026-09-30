package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiClientException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
