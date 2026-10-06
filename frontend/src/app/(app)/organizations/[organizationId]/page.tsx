"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { OrganizationRoleHint } from "@/components/organization/organization-role-hint";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { listTeams } from "@/lib/api/teams";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";

export default function OrganizationDetailPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
  });

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  const cards = [
    {
      title: "Teams",
      description: "Groups of people who own work in this organization.",
      href: `/organizations/${organizationId}/teams`,
      count: teamsQuery.data?.length,
    },
    {
      title: "Services",
      description: "Service catalog entries for this organization.",
      href: `/organizations/${organizationId}/services`,
      count: servicesQuery.data?.length,
    },
    {
      title: "Incidents",
      description: "Track production issues and response for this organization.",
      href: `/organizations/${organizationId}/incidents`,
    },
    {
      title: "Members",
      description: "People with access to this organization and their roles.",
      href: `/organizations/${organizationId}/members`,
      count: membersQuery.data?.length,
    },
  ];

  if (canManage) {
    cards.push({
      title: "Settings",
      description: "Update organization name and slug.",
      href: `/organizations/${organizationId}/settings`,
      count: undefined,
    });
  }

  return (
    <div className="space-y-6">
      <Link href="/organizations" className="text-sm text-primary hover:underline">
        ← All organizations
      </Link>

      {organizationQuery.isLoading && (
        <p className="text-sm text-muted-foreground">Loading organization…</p>
      )}

      {organization && (
        <>
          <section className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight">{organization.name}</h1>
            <p className="text-sm text-muted-foreground">Slug: {organization.slug}</p>
            <OrganizationRoleHint organizationRole={organization.currentUserRole} />
          </section>

          <OrganizationNav organizationId={organizationId} canManage={canManage} />

          <section className="grid gap-4 sm:grid-cols-2">
            {cards.map((card) => (
              <Link
                key={card.href}
                href={card.href}
                className="rounded-lg border p-4 transition-colors hover:bg-muted/40"
              >
                <h2 className="text-base font-semibold">{card.title}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{card.description}</p>
                {card.count !== undefined && (
                  <p className="mt-2 text-xs text-muted-foreground">{card.count} total</p>
                )}
              </Link>
            ))}
          </section>
        </>
      )}
    </div>
  );
}
