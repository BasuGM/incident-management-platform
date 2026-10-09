package com.example.incidentmanagement.incident;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPostmortemRepository extends JpaRepository<IncidentPostmortem, UUID> {

    boolean existsByOrganization_IdAndIncident_Id(UUID organizationId, UUID incidentId);

    @EntityGraph(attributePaths = {"author", "publishedBy"})
    Optional<IncidentPostmortem> findByOrganization_IdAndIncident_Id(UUID organizationId, UUID incidentId);

    @EntityGraph(attributePaths = {"author", "publishedBy"})
    Optional<IncidentPostmortem> findByIdAndOrganization_IdAndIncident_Id(
            UUID id, UUID organizationId, UUID incidentId);

    @EntityGraph(attributePaths = {"author", "incident", "publishedBy"})
    Page<IncidentPostmortem> findByOrganization_IdAndStatusOrderByPublishedAtDescCreatedAtDescIdDesc(
            UUID organizationId, IncidentPostmortemStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "incident", "publishedBy"})
    Page<IncidentPostmortem> findByOrganization_IdAndStatusOrderByCreatedAtDescIdDesc(
            UUID organizationId, IncidentPostmortemStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "incident", "publishedBy"})
    Page<IncidentPostmortem> findByOrganization_IdOrderByCreatedAtDescIdDesc(UUID organizationId, Pageable pageable);
}
