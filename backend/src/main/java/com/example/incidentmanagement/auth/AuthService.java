package com.example.incidentmanagement.auth;

import com.example.incidentmanagement.auth.dto.AuthResponse;
import com.example.incidentmanagement.auth.dto.LoginRequest;
import com.example.incidentmanagement.auth.dto.RegisterRequest;
import com.example.incidentmanagement.common.exception.InvalidCredentialsException;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import com.example.incidentmanagement.user.UserService;
import com.example.incidentmanagement.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserService userService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthTokens register(RegisterRequest request) {
        User user = userService.register(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                UserRole.ENGINEER);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthTokens login(LoginRequest request) {
        User user = userService.getByEmail(request.email());
        if (!user.isEnabled() || !userService.passwordMatches(user, request.password())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotateToken(rawRefreshToken);
        return issueTokens(rotation.user(), rotation.rawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private AuthTokens issueTokens(User user) {
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user);
        return issueTokens(user, refresh.rawToken());
    }

    private AuthTokens issueTokens(User user, String rawRefreshToken) {
        String accessToken = jwtService.generateAccessToken(user);
        AuthResponse response = new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtService.accessTokenExpirationSeconds(),
                UserResponse.publicView(user));
        return new AuthTokens(response, rawRefreshToken);
    }

    public record AuthTokens(AuthResponse response, String rawRefreshToken) {}
}
