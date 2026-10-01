package com.example.incidentmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.incidentmanagement.config.JwtProperties;
import com.example.incidentmanagement.support.IntegrationTestBase;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JwtSecurityContextIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void jwtPropertiesAreBoundForTests() {
        assertThat(jwtProperties.secret()).isEqualTo("test-jwt-secret-key-minimum-32-characters-long!!");
        assertThat(jwtProperties.accessTokenExpirationSeconds()).isEqualTo(900L);
    }

    @Test
    void accessTokenRoundTripInTestContext() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt@example.com");
        user.setRole(UserRole.ENGINEER);

        String token = jwtService.generateAccessToken(user);
        JwtService.JwtUserClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(user.getId());
    }
}
