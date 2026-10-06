"use client";

import { useOrganizations } from "@/components/organization/organization-provider";
import { usePathname, useRouter } from "next/navigation";

export function OrganizationSelector() {
  const { organizations, selectedOrganizationId, setSelectedOrganizationId, isLoading } =
    useOrganizations();
  const pathname = usePathname();
  const router = useRouter();

  if (isLoading) {
    return <span className="text-xs text-muted-foreground">Loading orgs…</span>;
  }

  if (!organizations.length) {
    return <span className="text-xs text-muted-foreground">No organization</span>;
  }

  return (
    <label className="flex items-center gap-2 text-xs">
      <span className="text-muted-foreground">Organization</span>
      <select
        className="h-8 rounded-md border border-input bg-background px-2 text-sm"
        value={selectedOrganizationId ?? ""}
        onChange={(event) => {
          const organizationId = event.target.value;
          setSelectedOrganizationId(organizationId);
          if (pathname.startsWith("/organizations/") && pathname !== "/organizations") {
            router.push(`/organizations/${organizationId}`);
          }
        }}
      >
        {organizations.map((organization) => (
          <option key={organization.id} value={organization.id}>
            {organization.name}
          </option>
        ))}
      </select>
    </label>
  );
}
