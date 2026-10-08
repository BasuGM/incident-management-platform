package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.ConflictException;
import com.example.incidentmanagement.common.exception.ForbiddenException;
import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationAuthorizationService;
import com.example.incidentmanagement.organization.OrganizationMember;
import com.example.incidentmanagement.organization.OrganizationRole;
import com.example.incidentmanagement.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IncidentCommentServiceTest {

    @Mock
    private IncidentCommentRepository incidentCommentRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorizationService;

    @InjectMocks
    private IncidentCommentService incidentCommentService;

    private UUID organizationId;
    private UUID incidentId;
    private UUID commentId;
    private UUID memberUserId;
    private UUID otherUserId;
    private Organization organization;
    private User memberUser;
    private User otherUser;
    private OrganizationMember member;
    private OrganizationMember adminMember;
    private OrganizationMember ownerMember;
    private OrganizationMember viewerMember;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        incidentId = UUID.randomUUID();
        commentId = UUID.randomUUID();
        memberUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        organization = new Organization();
        organization.setId(organizationId);

        memberUser = user(memberUserId);
        otherUser = user(otherUserId);

        member = membership(memberUser, OrganizationRole.MEMBER);
        adminMember = membership(memberUser, OrganizationRole.ADMIN);
        ownerMember = membership(memberUser, OrganizationRole.OWNER);
        viewerMember = membership(memberUser, OrganizationRole.VIEWER);
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private OrganizationMember membership(User user, OrganizationRole role) {
        OrganizationMember organizationMember = new OrganizationMember();
        organizationMember.setOrganization(organization);
        organizationMember.setUser(user);
        organizationMember.setRole(role);
        return organizationMember;
    }

    private Incident openIncident() {
        Incident incident = new Incident();
        ReflectionTestUtils.setField(incident, "id", incidentId);
        incident.setOrganization(organization);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setTitle("Outage");
        incident.setSeverity(IncidentSeverity.SEV2);
        return incident;
    }

    private IncidentComment commentFor(User author, String body) {
        IncidentComment comment = IncidentComment.create(organization, openIncident(), author, body);
        ReflectionTestUtils.setField(comment, "id", commentId);
        return comment;
    }

    private void stubWritableIncident(OrganizationMember caller) {
        when(organizationAuthorizationService.requireRole(
                        eq(organizationId),
                        eq(memberUserId),
                        eq(OrganizationRole.OWNER),
                        eq(OrganizationRole.ADMIN),
                        eq(OrganizationRole.MEMBER)))
                .thenReturn(caller);
        when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                .thenReturn(openIncident());
    }

    @Nested
    class Create {
        @Test
        void ownerCanCreate() {
            stubWritableIncident(ownerMember);
            when(incidentCommentRepository.save(any(IncidentComment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "Hello");

            verify(incidentCommentRepository).save(any(IncidentComment.class));
        }

        @Test
        void adminCanCreate() {
            stubWritableIncident(adminMember);
            when(incidentCommentRepository.save(any(IncidentComment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "Hello");

            verify(incidentCommentRepository).save(any(IncidentComment.class));
        }

        @Test
        void memberCanCreate() {
            stubWritableIncident(member);
            when(incidentCommentRepository.save(any(IncidentComment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "Hello");

            verify(incidentCommentRepository).save(any(IncidentComment.class));
        }

        @Test
        void viewerCannotCreate() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentCommentService.createComment(
                            organizationId, incidentId, memberUserId, "Hello"))
                    .isInstanceOf(ForbiddenException.class);

            verify(incidentCommentRepository, never()).save(any());
        }

        @Test
        void authorIsAuthenticatedCaller() {
            stubWritableIncident(member);
            ArgumentCaptor<IncidentComment> captor = ArgumentCaptor.forClass(IncidentComment.class);
            when(incidentCommentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "Hello");

            assertThat(captor.getValue().getAuthor().getId()).isEqualTo(memberUserId);
        }

        @Test
        void trimsBody() {
            stubWritableIncident(member);
            ArgumentCaptor<IncidentComment> captor = ArgumentCaptor.forClass(IncidentComment.class);
            when(incidentCommentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "  trimmed  ");

            assertThat(captor.getValue().getBody()).isEqualTo("trimmed");
        }

        @Test
        void preservesInternalWhitespace() {
            stubWritableIncident(member);
            ArgumentCaptor<IncidentComment> captor = ArgumentCaptor.forClass(IncidentComment.class);
            when(incidentCommentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "  foo   bar  ");

            assertThat(captor.getValue().getBody()).isEqualTo("foo   bar");
        }

        @Test
        void preservesMultilineBody() {
            stubWritableIncident(member);
            ArgumentCaptor<IncidentComment> captor = ArgumentCaptor.forClass(IncidentComment.class);
            when(incidentCommentRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

            String body = "  line one\nline two  ";
            incidentCommentService.createComment(organizationId, incidentId, memberUserId, body);

            assertThat(captor.getValue().getBody()).isEqualTo("line one\nline two");
        }

        @Test
        void rejectsEmptyBody() {
            stubWritableIncident(member);

            assertThatThrownBy(() -> incidentCommentService.createComment(organizationId, incidentId, memberUserId, ""))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void rejectsWhitespaceOnlyBody() {
            stubWritableIncident(member);

            assertThatThrownBy(() -> incidentCommentService.createComment(organizationId, incidentId, memberUserId, "   "))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void rejectsBodyOver5000Characters() {
            stubWritableIncident(member);
            String tooLong = "x".repeat(5001);

            assertThatThrownBy(() -> incidentCommentService.createComment(
                            organizationId, incidentId, memberUserId, tooLong))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    class Read {
        @Test
        void ownerCanList() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            Page<IncidentComment> page = new PageImpl<>(List.of());
            when(incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                            organizationId, incidentId, PageRequest.of(0, 10)))
                    .thenReturn(page);

            assertThat(incidentCommentService.listComments(
                            organizationId, incidentId, memberUserId, PageRequest.of(0, 10)))
                    .isSameAs(page);
        }

        @Test
        void adminCanList() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                            eq(organizationId), eq(incidentId), any(Pageable.class)))
                    .thenReturn(Page.empty());

            incidentCommentService.listComments(organizationId, incidentId, memberUserId, PageRequest.of(0, 10));

            verify(organizationAuthorizationService).requireIncidentInOrganization(organizationId, incidentId, memberUserId);
        }

        @Test
        void memberCanList() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                            eq(organizationId), eq(incidentId), any(Pageable.class)))
                    .thenReturn(Page.empty());

            incidentCommentService.listComments(organizationId, incidentId, memberUserId, PageRequest.of(0, 10));
        }

        @Test
        void viewerCanList() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                            eq(organizationId), eq(incidentId), any(Pageable.class)))
                    .thenReturn(Page.empty());

            incidentCommentService.listComments(organizationId, incidentId, memberUserId, PageRequest.of(0, 10));
        }

        @Test
        void enforcesIncidentAuthorizationOnList() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentCommentService.listComments(
                            organizationId, incidentId, memberUserId, PageRequest.of(0, 10)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Update {
        private void stubUpdateContext(OrganizationMember caller, IncidentComment comment) {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(caller);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, incidentId))
                    .thenReturn(Optional.of(comment));
        }

        @Test
        void authorCanUpdateOwnComment() {
            IncidentComment comment = commentFor(memberUser, "old");
            stubUpdateContext(member, comment);
            when(incidentCommentRepository.save(comment)).thenReturn(comment);

            IncidentComment updated = incidentCommentService.updateComment(
                    organizationId, incidentId, commentId, memberUserId, "  new  ");

            assertThat(updated.getBody()).isEqualTo("new");
        }

        @Test
        void authorCannotUpdateDeletedComment() {
            IncidentComment comment = commentFor(memberUser, "old");
            comment.markDeleted();
            stubUpdateContext(member, comment);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("COMMENT_NOT_EDITABLE");
        }

        @Test
        void authorCannotUpdateOnTerminalIncident() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.RESOLVED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("INCIDENT_NOT_EDITABLE");
        }

        @Test
        void anotherMemberCannotUpdate() {
            IncidentComment comment = commentFor(otherUser, "old");
            stubUpdateContext(member, comment);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void ownerCannotEditAnotherUsersComment() {
            IncidentComment comment = commentFor(otherUser, "old");
            stubUpdateContext(ownerMember, comment);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void adminCannotEditAnotherUsersComment() {
            IncidentComment comment = commentFor(otherUser, "old");
            stubUpdateContext(adminMember, comment);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void viewerCannotUpdate() {
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenThrow(new ForbiddenException());

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "new"))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void bodyValidationAppliesOnUpdate() {
            IncidentComment comment = commentFor(memberUser, "old");
            stubUpdateContext(member, comment);

            assertThatThrownBy(() -> incidentCommentService.updateComment(
                            organizationId, incidentId, commentId, memberUserId, "   "))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    class Delete {
        private void stubDeleteContext(OrganizationMember caller, IncidentComment comment) {
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(caller);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, incidentId))
                    .thenReturn(Optional.of(comment));
        }

        @Test
        void authorCanDeleteOwnComment() {
            IncidentComment comment = commentFor(memberUser, "secret");
            stubDeleteContext(member, comment);
            when(incidentCommentRepository.save(comment)).thenReturn(comment);

            IncidentComment deleted = incidentCommentService.deleteComment(
                    organizationId, incidentId, commentId, memberUserId);

            assertThat(deleted.isDeleted()).isTrue();
            assertThat(deleted.getBody()).isEmpty();
        }

        @Test
        void memberCanDeleteOwnComment() {
            IncidentComment comment = commentFor(memberUser, "mine");
            stubDeleteContext(member, comment);
            when(incidentCommentRepository.save(comment)).thenReturn(comment);

            incidentCommentService.deleteComment(organizationId, incidentId, commentId, memberUserId);

            assertThat(comment.isDeleted()).isTrue();
        }

        @Test
        void ownerCanDeleteAnotherUsersComment() {
            IncidentComment comment = commentFor(otherUser, "moderate");
            OrganizationMember owner = membership(memberUser, OrganizationRole.OWNER);
            stubDeleteContext(owner, comment);
            when(incidentCommentRepository.save(comment)).thenReturn(comment);

            incidentCommentService.deleteComment(organizationId, incidentId, commentId, memberUserId);

            assertThat(comment.isDeleted()).isTrue();
        }

        @Test
        void adminCanDeleteAnotherUsersComment() {
            IncidentComment comment = commentFor(otherUser, "moderate");
            OrganizationMember admin = membership(memberUser, OrganizationRole.ADMIN);
            stubDeleteContext(admin, comment);
            when(incidentCommentRepository.save(comment)).thenReturn(comment);

            incidentCommentService.deleteComment(organizationId, incidentId, commentId, memberUserId);

            assertThat(comment.isDeleted()).isTrue();
        }

        @Test
        void memberCannotDeleteAnotherUsersComment() {
            IncidentComment comment = commentFor(otherUser, "not yours");
            stubDeleteContext(member, comment);

            assertThatThrownBy(() -> incidentCommentService.deleteComment(
                            organizationId, incidentId, commentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(comment.isDeleted()).isFalse();
        }

        @Test
        void viewerCannotDelete() {
            when(organizationAuthorizationService.requireMembership(organizationId, memberUserId))
                    .thenReturn(viewerMember);

            assertThatThrownBy(() -> incidentCommentService.deleteComment(
                            organizationId, incidentId, commentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void repeatedDeleteIsRejected() {
            IncidentComment comment = commentFor(memberUser, "gone");
            comment.markDeleted();
            stubDeleteContext(member, comment);

            assertThatThrownBy(() -> incidentCommentService.deleteComment(
                            organizationId, incidentId, commentId, memberUserId))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("COMMENT_NOT_EDITABLE");
        }
    }

    @Nested
    class TenantIsolation {
        @Test
        void crossOrgCommentAccessForbidden() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, incidentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentCommentService.getComment(
                            organizationId, incidentId, commentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void crossIncidentCommentAccessForbidden() {
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, incidentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentCommentService.getComment(
                            organizationId, incidentId, commentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void commentIdAloneIsNotSufficient() {
            UUID otherIncidentId = UUID.randomUUID();
            when(organizationAuthorizationService.requireIncidentInOrganization(
                            organizationId, otherIncidentId, memberUserId))
                    .thenReturn(openIncident());
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, otherIncidentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentCommentService.getComment(
                            organizationId, otherIncidentId, commentId, memberUserId))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    class Lifecycle {
        @Test
        void openAllowsWrites() {
            stubWritableIncident(member);
            when(incidentCommentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "ok");
        }

        @Test
        void acknowledgedAllowsWrites() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            when(incidentCommentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            incidentCommentService.createComment(organizationId, incidentId, memberUserId, "ok");
        }

        @Test
        void resolvedRejectsWritesWith409() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.RESOLVED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(member);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentCommentService.createComment(
                            organizationId, incidentId, memberUserId, "nope"))
                    .isInstanceOf(ConflictException.class)
                    .extracting(ex -> ((ConflictException) ex).getErrorCode())
                    .isEqualTo("INCIDENT_NOT_EDITABLE");
        }

        @Test
        void cancelledRejectsWritesWith409() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.CANCELLED);
            when(organizationAuthorizationService.requireRole(
                            eq(organizationId),
                            eq(memberUserId),
                            eq(OrganizationRole.OWNER),
                            eq(OrganizationRole.ADMIN),
                            eq(OrganizationRole.MEMBER)))
                    .thenReturn(adminMember);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);

            assertThatThrownBy(() -> incidentCommentService.createComment(
                            organizationId, incidentId, memberUserId, "nope"))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void readsAllowedOnResolvedIncident() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.RESOLVED);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            when(incidentCommentRepository.findByOrganization_IdAndIncident_IdOrderByCreatedAtAscIdAsc(
                            eq(organizationId), eq(incidentId), any(Pageable.class)))
                    .thenReturn(Page.empty());

            incidentCommentService.listComments(organizationId, incidentId, memberUserId, PageRequest.of(0, 10));
        }

        @Test
        void readsAllowedOnCancelledIncident() {
            Incident incident = openIncident();
            incident.setStatus(IncidentStatus.CANCELLED);
            when(organizationAuthorizationService.requireIncidentInOrganization(organizationId, incidentId, memberUserId))
                    .thenReturn(incident);
            IncidentComment comment = commentFor(memberUser, "history");
            when(incidentCommentRepository.findByIdAndOrganization_IdAndIncident_Id(
                            commentId, organizationId, incidentId))
                    .thenReturn(Optional.of(comment));

            assertThat(incidentCommentService.getComment(organizationId, incidentId, commentId, memberUserId))
                    .isEqualTo(comment);
        }
    }
}
