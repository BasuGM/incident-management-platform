package com.example.incidentmanagement.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container for the entire test JVM. Integration tests share this instance so
 * Testcontainers does not stop the database when one test class finishes while another Spring
 * context still uses the same JDBC URL.
 */
final class SharedPostgresContainer {

    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("incident_management_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        CONTAINER.start();
    }

    private SharedPostgresContainer() {}

    static PostgreSQLContainer<?> get() {
        return CONTAINER;
    }
}
