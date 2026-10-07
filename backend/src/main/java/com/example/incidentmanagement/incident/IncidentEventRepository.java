package com.example.incidentmanagement.incident;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {

    @EntityGraph(attributePaths = {"actor"})
    Page<IncidentEvent> findByOrganization_IdAndIncident_IdOrderByCreatedAtDescIdDesc(
            UUID organizationId, UUID incidentId, Pageable pageable);
}
