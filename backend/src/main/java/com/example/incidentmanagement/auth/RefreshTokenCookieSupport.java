package com.example.incidentmanagement.auth;

import com.example.incidentmanagement.config.AuthProperties;
import com.example.incidentmanagement.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieSupport {

    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties authProperties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookieSupport(AuthProperties authProperties, JwtProperties jwtProperties) {
        this.authProperties = authProperties;
        this.jwtProperties = jwtProperties;
    }

    public void writeRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.refreshCookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenExpirationDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(authProperties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .path(COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
