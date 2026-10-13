package com.example.incidentmanagement.oncall;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnCallScheduleRepository extends JpaRepository<OnCallSchedule, UUID> {

    @EntityGraph(attributePaths = {"team", "service", "createdBy", "updatedBy"})
    Optional<OnCallSchedule> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = {"team", "service", "createdBy", "updatedBy"})
    List<OnCallSchedule> findByOrganization_IdOrderByNameAsc(UUID organizationId);

    @EntityGraph(attributePaths = {"team", "service", "createdBy", "updatedBy"})
    List<OnCallSchedule> findByOrganization_IdAndStatusOrderByNameAsc(
            UUID organizationId, OnCallScheduleStatus status);

    boolean existsByOrganization_IdAndNameIgnoreCase(UUID organizationId, String name);

    boolean existsByOrganization_IdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);
}
