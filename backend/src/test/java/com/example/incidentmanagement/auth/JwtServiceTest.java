package com.example.incidentmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.config.JwtProperties;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(
                "test-jwt-secret-key-minimum-32-characters-long!!", 900, 7));
    }

    @Test
    void generatesAndParsesAccessToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(UserRole.ENGINEER);

        String token = jwtService.generateAccessToken(user);
        JwtService.JwtUserClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(user.getId());
        assertThat(claims.email()).isEqualTo("user@example.com");
        assertThat(claims.role()).isEqualTo(UserRole.ENGINEER);
    }

    @Test
    void rejectsInvalidToken() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-token"))
                .isInstanceOf(com.example.incidentmanagement.common.exception.UnauthorizedException.class);
    }
}
