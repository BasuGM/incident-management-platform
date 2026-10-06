package com.example.incidentmanagement.incident;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationIncidentCounterRepository
        extends JpaRepository<OrganizationIncidentCounter, UUID> {}
