package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class PostmortemNotFoundException extends ApiClientException {

    public PostmortemNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POSTMORTEM_NOT_FOUND", "Postmortem not found");
    }
}
