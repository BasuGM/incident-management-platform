package com.example.incidentmanagement.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiClientException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }
}
