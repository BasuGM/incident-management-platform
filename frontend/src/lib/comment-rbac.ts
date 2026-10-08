import type { OrganizationRole } from "@/types/organization";
import type { IncidentComment } from "@/types/incident";

export const COMMENT_MAX_BODY_LENGTH = 5000;

export function canCreateComment(role: OrganizationRole, incidentWritable: boolean) {
  return incidentWritable && role !== "VIEWER";
}

export function canEditComment(
  role: OrganizationRole,
  comment: IncidentComment,
  currentUserId: string,
  incidentWritable: boolean,
) {
  if (!incidentWritable || role === "VIEWER" || comment.deleted) {
    return false;
  }
  return comment.authorId === currentUserId;
}

export function canDeleteComment(
  role: OrganizationRole,
  comment: IncidentComment,
  currentUserId: string,
  incidentWritable: boolean,
) {
  if (!incidentWritable || role === "VIEWER" || comment.deleted) {
    return false;
  }
  if (comment.authorId === currentUserId) {
    return true;
  }
  return role === "OWNER" || role === "ADMIN";
}
