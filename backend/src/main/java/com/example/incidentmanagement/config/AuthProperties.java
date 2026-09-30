package com.example.incidentmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String refreshCookieName, boolean refreshCookieSecure) {}
