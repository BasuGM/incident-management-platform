package com.example.incidentmanagement.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<OrganizationMember> findByOrganizationId(UUID organizationId);

    List<OrganizationMember> findByUserId(UUID userId);

    long countByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);

    @Query(
            """
            select om.organization from OrganizationMember om
            where om.user.id = :userId
            order by om.organization.name asc
            """)
    List<Organization> findOrganizationsForUser(@Param("userId") UUID userId);
}
