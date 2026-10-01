package com.example.incidentmanagement;

import com.example.incidentmanagement.config.AuthProperties;
import com.example.incidentmanagement.config.BootstrapAdminProperties;
import com.example.incidentmanagement.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, BootstrapAdminProperties.class})
public class IncidentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentManagementApplication.class, args);
    }
}
