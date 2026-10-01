package com.example.incidentmanagement.config;

import com.example.incidentmanagement.auth.JwtService;
import com.example.incidentmanagement.security.JwtAuthenticationFilter;
import com.example.incidentmanagement.user.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JwtFilterConfig {

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        return new JwtAuthenticationFilter(jwtService, userService);
    }
}
