package com.example.incidentmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.auth.dto.LoginRequest;
import com.example.incidentmanagement.common.exception.InvalidCredentialsException;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import com.example.incidentmanagement.user.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userService, jwtService, refreshTokenService);
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("john@example.com");
        user.setEnabled(true);
        user.setRole(UserRole.ENGINEER);

        when(userService.getByEmail("john@example.com")).thenReturn(user);
        when(userService.passwordMatches(user, "wrong")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("john@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginReturnsTokensForValidCredentials() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("john@example.com");
        user.setFirstName("John");
        user.setLastName("Smith");
        user.setEnabled(true);
        user.setRole(UserRole.ENGINEER);

        when(userService.getByEmail("john@example.com")).thenReturn(user);
        when(userService.passwordMatches(user, "Password1")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access");
        when(jwtService.accessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh", new RefreshToken()));

        AuthService.AuthTokens tokens = authService.login(new LoginRequest("john@example.com", "Password1"));

        assertThat(tokens.response().accessToken()).isEqualTo("access");
        assertThat(tokens.response().refreshToken()).isEqualTo("refresh");
        assertThat(tokens.response().user().email()).isEqualTo("john@example.com");
    }
}
