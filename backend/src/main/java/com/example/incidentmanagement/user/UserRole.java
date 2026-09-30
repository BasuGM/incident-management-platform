package com.example.incidentmanagement.user;

public enum UserRole {
    ADMIN,
    ENGINEER,
    VIEWER;

    public String authority() {
        return "ROLE_" + name();
    }
}
