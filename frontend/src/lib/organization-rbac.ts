import type { OrganizationRole } from "@/types/organization";

/** Organization-level mutations (teams, services, members, etc.) */
export function canManageOrganization(role: OrganizationRole) {
  return role === "OWNER" || role === "ADMIN";
}
