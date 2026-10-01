package com.example.incidentmanagement.config;

import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import com.example.incidentmanagement.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final BootstrapAdminProperties properties;
    private final Environment environment;
    private final UserRepository userRepository;
    private final UserService userService;

    public BootstrapAdminRunner(
            BootstrapAdminProperties properties,
            Environment environment,
            UserRepository userRepository,
            UserService userService) {
        this.properties = properties;
        this.environment = environment;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isProductionProfile()) {
            log.warn("Bootstrap admin is enabled but production profile is active; skipping bootstrap");
            return;
        }
        if (properties.email() == null
                || properties.email().isBlank()
                || properties.password() == null
                || properties.password().isBlank()) {
            log.warn("Bootstrap admin enabled but email/password not configured; skipping");
            return;
        }

        String email = UserService.normalizeEmail(properties.email());
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(this::promoteToAdmin, this::createAdminUser);
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private void promoteToAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("Promoted existing user to global ADMIN role for development bootstrap");
        }
    }

    private void createAdminUser() {
        User user = userService.register(
                properties.email(),
                properties.password(),
                "Bootstrap",
                "Admin",
                UserRole.ADMIN);
        log.info("Created bootstrap global ADMIN user with id {}", user.getId());
    }
}
