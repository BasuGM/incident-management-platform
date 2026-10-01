package com.example.incidentmanagement.support;

import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static MockMvc buildMockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        var postgres = SharedPostgresContainer.get();
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
