package com.example.incidentmanagement.service;

import com.example.incidentmanagement.security.UserPrincipal;
import com.example.incidentmanagement.service.dto.CreateServiceRequest;
import com.example.incidentmanagement.service.dto.ServiceResponse;
import com.example.incidentmanagement.service.dto.UpdateServiceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public List<ServiceResponse> listServices(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UserPrincipal principal) {
        return serviceService.listServices(organizationId, principal.getId());
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateServiceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ServiceResponse response = serviceService.createService(organizationId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{serviceId}")
    public ServiceResponse getService(
            @PathVariable UUID organizationId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return serviceService.getService(organizationId, serviceId, principal.getId());
    }

    @PatchMapping("/{serviceId}")
    public ServiceResponse updateService(
            @PathVariable UUID organizationId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return serviceService.updateService(organizationId, serviceId, request, principal.getId());
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(
            @PathVariable UUID organizationId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        serviceService.deleteService(organizationId, serviceId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
