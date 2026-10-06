package com.example.incidentmanagement.incident;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates monotonic incident numbers per organization using a single atomic PostgreSQL upsert.
 * <p>
 * The counter row stores the next value to hand out after increment. Initial insert seeds
 * {@code next_number = 2} so the first {@code RETURNING next_number - 1} yields {@code 1}.
 */
@Component
public class IncidentNumberAllocator {

    private static final String ALLOCATE_SQL =
            """
            INSERT INTO app.organization_incident_counters (organization_id, next_number)
            VALUES (?, 2)
            ON CONFLICT (organization_id) DO UPDATE
            SET next_number = app.organization_incident_counters.next_number + 1
            RETURNING next_number - 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public IncidentNumberAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long allocateNextIncidentNumber(UUID organizationId) {
        Long assigned =
                jdbcTemplate.queryForObject(ALLOCATE_SQL, Long.class, organizationId);
        if (assigned == null) {
            throw new IllegalStateException("Failed to allocate incident number for organization " + organizationId);
        }
        return assigned;
    }
}
