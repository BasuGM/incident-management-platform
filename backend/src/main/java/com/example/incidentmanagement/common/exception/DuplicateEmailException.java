package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends ApiClientException {

    public DuplicateEmailException() {
        super(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "A user with this email already exists");
    }
}
