package com.example.incidentmanagement.team.dto;

import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(@Size(max = 100) String name, @Size(max = 500) String description) {}
