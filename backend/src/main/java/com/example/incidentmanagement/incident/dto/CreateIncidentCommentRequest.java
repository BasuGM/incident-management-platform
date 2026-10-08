package com.example.incidentmanagement.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentCommentRequest(@NotBlank @Size(max = 5000) String body) {}
