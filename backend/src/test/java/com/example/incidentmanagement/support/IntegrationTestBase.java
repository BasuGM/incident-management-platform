package com.example.incidentmanagement.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("incident_management_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.redis.enabled", () -> "false");
        registry.add(
                "app.jwt.secret",
                () -> "test-jwt-secret-key-minimum-32-characters-long!!");
        registry.add("app.jwt.access-token-expiration-seconds", () -> "900");
        registry.add("app.jwt.refresh-token-expiration-days", () -> "7");
        registry.add("app.auth.refresh-cookie-name", () -> "refresh_token");
        registry.add("app.auth.refresh-cookie-secure", () -> "false");
    }
}
