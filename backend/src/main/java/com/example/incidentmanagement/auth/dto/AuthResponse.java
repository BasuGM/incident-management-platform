package com.example.incidentmanagement.auth.dto;

import com.example.incidentmanagement.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {}
