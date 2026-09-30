package com.example.incidentmanagement.auth;

import com.example.incidentmanagement.auth.dto.AuthResponse;
import com.example.incidentmanagement.auth.dto.LoginRequest;
import com.example.incidentmanagement.auth.dto.RefreshRequest;
import com.example.incidentmanagement.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieSupport refreshTokenCookieSupport;

    public AuthController(AuthService authService, RefreshTokenCookieSupport refreshTokenCookieSupport) {
        this.authService = authService;
        this.refreshTokenCookieSupport = refreshTokenCookieSupport;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.register(request);
        refreshTokenCookieSupport.writeRefreshCookie(response, tokens.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(tokens.response());
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.login(request);
        refreshTokenCookieSupport.writeRefreshCookie(response, tokens.rawRefreshToken());
        return tokens.response();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @RequestBody(required = false) RefreshRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        String rawRefreshToken = resolveRefreshToken(requestBody, request);
        AuthService.AuthTokens tokens = authService.refresh(rawRefreshToken);
        refreshTokenCookieSupport.writeRefreshCookie(response, tokens.rawRefreshToken());
        return tokens.response();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = refreshTokenCookieSupport.readRefreshCookie(request);
        authService.logout(rawRefreshToken);
        refreshTokenCookieSupport.clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private String resolveRefreshToken(RefreshRequest requestBody, HttpServletRequest request) {
        if (requestBody != null
                && requestBody.refreshToken() != null
                && !requestBody.refreshToken().isBlank()) {
            return requestBody.refreshToken();
        }
        String cookieValue = refreshTokenCookieSupport.readRefreshCookie(request);
        if (cookieValue == null || cookieValue.isBlank()) {
            throw new com.example.incidentmanagement.common.exception.InvalidRefreshTokenException();
        }
        return cookieValue;
    }
}
