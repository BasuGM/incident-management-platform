package com.example.incidentmanagement.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRepository extends JpaRepository<Service, UUID> {

    @Query(
            "SELECT s FROM Service s LEFT JOIN FETCH s.team WHERE s.organization.id = :organizationId ORDER BY s.name ASC")
    List<Service> findByOrganizationIdOrderByNameAsc(@Param("organizationId") UUID organizationId);

    @Query(
            "SELECT s FROM Service s LEFT JOIN FETCH s.team WHERE s.id = :serviceId AND s.organization.id = :organizationId")
    Optional<Service> findByIdAndOrganizationId(@Param("serviceId") UUID serviceId, @Param("organizationId") UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);
}
