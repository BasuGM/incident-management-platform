package com.example.incidentmanagement.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organization_incident_counters", schema = "app")
public class OrganizationIncidentCounter {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "next_number", nullable = false)
    private long nextNumber;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public long getNextNumber() {
        return nextNumber;
    }

    public void setNextNumber(long nextNumber) {
        this.nextNumber = nextNumber;
    }
}
