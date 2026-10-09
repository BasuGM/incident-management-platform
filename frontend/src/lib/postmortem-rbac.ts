import type { IncidentPostmortem } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";

export const POSTMORTEM_TITLE_MAX_LENGTH = 200;
export const POSTMORTEM_SECTION_MAX_LENGTH = 10000;

export function canCreatePostmortem(role: OrganizationRole, incidentResolved: boolean) {
  return incidentResolved && role !== "VIEWER";
}

function isAuthor(postmortem: IncidentPostmortem, currentUserId: string) {
  return postmortem.authorId === currentUserId;
}

function canManageAnyDraft(role: OrganizationRole) {
  return role === "OWNER" || role === "ADMIN";
}

export function canEditPostmortemDraft(
  role: OrganizationRole,
  postmortem: IncidentPostmortem,
  currentUserId: string,
) {
  if (postmortem.status !== "DRAFT" || role === "VIEWER") {
    return false;
  }
  if (canManageAnyDraft(role)) {
    return true;
  }
  return role === "MEMBER" && isAuthor(postmortem, currentUserId);
}

export function canPublishPostmortem(
  role: OrganizationRole,
  postmortem: IncidentPostmortem,
  currentUserId: string,
) {
  return canEditPostmortemDraft(role, postmortem, currentUserId);
}

export function canUnpublishPostmortem(
  role: OrganizationRole,
  postmortem: IncidentPostmortem,
  currentUserId: string,
) {
  if (postmortem.status !== "PUBLISHED" || role === "VIEWER") {
    return false;
  }
  if (canManageAnyDraft(role)) {
    return true;
  }
  return role === "MEMBER" && isAuthor(postmortem, currentUserId);
}

export function canDeletePostmortemDraft(
  role: OrganizationRole,
  postmortem: IncidentPostmortem,
  currentUserId: string,
) {
  return canEditPostmortemDraft(role, postmortem, currentUserId);
}

export function canArchivePostmortem(role: OrganizationRole, postmortem: IncidentPostmortem) {
  return postmortem.status === "PUBLISHED" && canManageAnyDraft(role);
}

export function canUnarchivePostmortem(role: OrganizationRole, postmortem: IncidentPostmortem) {
  return postmortem.status === "ARCHIVED" && canManageAnyDraft(role);
}

export type PostmortemActionVisibility = {
  showCreate: boolean;
  showEdit: boolean;
  showPublish: boolean;
  showUnpublish: boolean;
  showDelete: boolean;
  showArchive: boolean;
  showUnarchive: boolean;
};

export function getPostmortemActionVisibility(
  role: OrganizationRole,
  postmortem: IncidentPostmortem | undefined,
  currentUserId: string,
  incidentResolved: boolean,
): PostmortemActionVisibility {
  const showCreate = !postmortem && canCreatePostmortem(role, incidentResolved);

  if (!postmortem) {
    return {
      showCreate,
      showEdit: false,
      showPublish: false,
      showUnpublish: false,
      showDelete: false,
      showArchive: false,
      showUnarchive: false,
    };
  }

  return {
    showCreate: false,
    showEdit: canEditPostmortemDraft(role, postmortem, currentUserId),
    showPublish: canPublishPostmortem(role, postmortem, currentUserId),
    showUnpublish: canUnpublishPostmortem(role, postmortem, currentUserId),
    showDelete: canDeletePostmortemDraft(role, postmortem, currentUserId),
    showArchive: canArchivePostmortem(role, postmortem),
    showUnarchive: canUnarchivePostmortem(role, postmortem),
  };
}
