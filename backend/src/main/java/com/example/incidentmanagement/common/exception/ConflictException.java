package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiClientException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
