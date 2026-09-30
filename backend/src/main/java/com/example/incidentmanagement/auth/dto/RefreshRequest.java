package com.example.incidentmanagement.auth.dto;

import jakarta.validation.constraints.Size;

public record RefreshRequest(@Size(max = 512) String refreshToken) {}
