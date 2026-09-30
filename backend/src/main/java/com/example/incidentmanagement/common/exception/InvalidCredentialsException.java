package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiClientException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}
