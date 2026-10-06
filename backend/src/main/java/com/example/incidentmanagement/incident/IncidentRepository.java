package com.example.incidentmanagement.incident;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    @EntityGraph(attributePaths = {"organization", "service", "reporter", "commander"})
    @Query(
            "SELECT i FROM Incident i WHERE i.id = :incidentId AND i.organization.id = :organizationId")
    Optional<Incident> findByIdAndOrganizationId(
            @Param("incidentId") UUID incidentId, @Param("organizationId") UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "service", "reporter", "commander"})
    @Query("SELECT i FROM Incident i WHERE i.organization.id = :organizationId")
    Page<Incident> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    boolean existsByOrganization_IdAndIncidentNumber(UUID organizationId, long incidentNumber);
}
