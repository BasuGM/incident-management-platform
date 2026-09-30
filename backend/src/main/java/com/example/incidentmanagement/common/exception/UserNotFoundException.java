package com.example.incidentmanagement.common.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiClientException {

    public UserNotFoundException(UUID userId) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found: " + userId);
    }
}
