import type { OrganizationRole } from "@/types/organization";

/** Organization-level mutations (teams, services, members, etc.) */
export function canManageOrganization(role: OrganizationRole) {
  return role === "OWNER" || role === "ADMIN";
}

export function canCreateIncident(role: OrganizationRole) {
  return role !== "VIEWER";
}

export function canUpdateIncident(role: OrganizationRole) {
  return role !== "VIEWER";
}

export function canCancelIncident(role: OrganizationRole) {
  return role === "OWNER" || role === "ADMIN";
}
