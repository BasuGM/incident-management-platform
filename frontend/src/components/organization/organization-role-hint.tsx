import { canManageOrganization } from "@/lib/organization-rbac";
import type { OrganizationRole } from "@/types/organization";

type OrganizationRoleHintProps = {
  organizationRole: OrganizationRole;
};

export function OrganizationRoleHint({ organizationRole }: OrganizationRoleHintProps) {
  const canManage = canManageOrganization(organizationRole);

  return (
    <p className="text-sm text-muted-foreground">
      Your organization role: <span className="font-medium text-foreground">{organizationRole}</span>
      {canManage
        ? " — you can manage teams, services, and members in this organization."
        : " — you have read access to organization resources."}
    </p>
  );
}
