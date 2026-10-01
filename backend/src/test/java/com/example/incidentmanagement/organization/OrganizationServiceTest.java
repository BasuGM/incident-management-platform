package com.example.incidentmanagement.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.organization.dto.CreateOrganizationRequest;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import com.example.incidentmanagement.user.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void createOrganizationAssignsOwnerMembership() {
        UUID userId = UUID.randomUUID();
        User creator = new User();
        creator.setId(userId);
        creator.setRole(UserRole.ENGINEER);

        when(userService.getById(userId)).thenReturn(creator);
        when(organizationRepository.existsBySlug("acme")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
            Organization org = invocation.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });
        when(organizationMemberRepository.save(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = organizationService.createOrganization(
                new CreateOrganizationRequest("Acme", "acme"), userId);

        assertThat(response.currentUserRole()).isEqualTo(OrganizationRole.OWNER);
        ArgumentCaptor<OrganizationMember> captor = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(organizationMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(OrganizationRole.OWNER);
    }
}
