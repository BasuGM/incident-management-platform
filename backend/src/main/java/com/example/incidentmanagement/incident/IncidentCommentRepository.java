package com.example.incidentmanagement.incident;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, UUID> {

    @EntityGraph(attributePaths = {"author"})
    Page<IncidentComment> findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
            UUID organizationId, UUID incidentId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<IncidentComment> findByIdAndOrganization_IdAndIncident_Id(
            UUID id, UUID organizationId, UUID incidentId);
}
