package com.example.incidentmanagement.user.dto;

import com.example.incidentmanagement.user.UserRole;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        UserRole role,
        Boolean enabled) {}
