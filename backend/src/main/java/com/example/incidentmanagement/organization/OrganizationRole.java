package com.example.incidentmanagement.organization;

public enum OrganizationRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER;

    public boolean canManageTeams() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageOrganization() {
        return this == OWNER;
    }

    public boolean canRead() {
        return true;
    }
}
