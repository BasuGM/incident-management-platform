package com.example.incidentmanagement.incident;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.service.Service;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRepository;
import com.example.incidentmanagement.user.UserRole;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

final class IncidentTestFixtures {

    private IncidentTestFixtures() {}

    static User createUser(UserRepository userRepository, PasswordEncoder passwordEncoder, String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setFirstName("Test");
        user.setLastName("User");
        user.setRole(UserRole.ENGINEER);
        return userRepository.save(user);
    }

    static Organization createOrganization(OrganizationRepository organizationRepository, String slug) {
        Organization organization = new Organization();
        organization.setName("Org " + slug);
        organization.setSlug(slug);
        return organizationRepository.save(organization);
    }

    static Service createService(
            ServiceRepository serviceRepository, Organization organization, String slug) {
        Service service = new Service();
        service.setOrganization(organization);
        service.setName(slug);
        service.setSlug(slug);
        return serviceRepository.save(service);
    }

    static Incident newIncident(
            Organization organization,
            long incidentNumber,
            User reporter,
            String title,
            IncidentSeverity severity) {
        Incident incident = new Incident();
        incident.setOrganization(organization);
        incident.setIncidentNumber(incidentNumber);
        incident.setReporter(reporter);
        incident.setTitle(title);
        incident.setSeverity(severity);
        return incident;
    }

    static String uniqueSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
