"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { createTeam, listTeams } from "@/lib/api/teams";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function OrganizationDetailPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const queryClient = useQueryClient();
  const [teamName, setTeamName] = useState("");
  const [teamDescription, setTeamDescription] = useState("");

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

  const createTeamMutation = useMutation({
    mutationFn: () =>
      createTeam(organizationId, {
        name: teamName,
        description: teamDescription || undefined,
      }),
    onSuccess: async () => {
      setTeamName("");
      setTeamDescription("");
      await queryClient.invalidateQueries({ queryKey: ["teams", organizationId] });
    },
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  return (
    <div className="space-y-8">
      {organizationQuery.isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
      {organization && (
        <>
          <section className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight">{organization.name}</h1>
            <p className="text-sm text-muted-foreground">
              Slug: {organization.slug} · Your role: {organization.currentUserRole}
            </p>
          </section>

          <section className="space-y-3">
            <h2 className="text-base font-semibold">Members</h2>
            {membersQuery.isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
            <ul className="space-y-2 text-sm">
              {membersQuery.data?.map((member) => (
                <li key={member.id} className="rounded border px-3 py-2">
                  {member.firstName} {member.lastName} ({member.email}) — {member.role}
                </li>
              ))}
            </ul>
          </section>

          <section className="space-y-4">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-base font-semibold">Teams</h2>
              <Link
                href={`/organizations/${organizationId}/teams`}
                className="text-sm text-primary hover:underline"
              >
                View all teams
              </Link>
            </div>
            {canManage && (
              <form
                className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  createTeamMutation.mutate();
                }}
              >
                <div className="space-y-2">
                  <Label htmlFor="team-name">Team name</Label>
                  <Input
                    id="team-name"
                    value={teamName}
                    onChange={(event) => setTeamName(event.target.value)}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="team-description">Description</Label>
                  <Input
                    id="team-description"
                    value={teamDescription}
                    onChange={(event) => setTeamDescription(event.target.value)}
                  />
                </div>
                <div className="sm:col-span-2">
                  <Button type="submit" disabled={createTeamMutation.isPending}>
                    Create team
                  </Button>
                </div>
              </form>
            )}
            <ul className="space-y-2 text-sm">
              {teamsQuery.data?.map((team) => (
                <li key={team.id}>
                  <Link
                    href={`/organizations/${organizationId}/teams/${team.id}`}
                    className="text-primary hover:underline"
                  >
                    {team.name}
                  </Link>
                  {team.description ? ` — ${team.description}` : ""}
                </li>
              ))}
            </ul>
          </section>

          <section className="space-y-4">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-base font-semibold">Services</h2>
              <Link
                href={`/organizations/${organizationId}/services`}
                className="text-sm text-primary hover:underline"
              >
                View all services
              </Link>
            </div>
            {servicesQuery.isLoading && (
              <p className="text-sm text-muted-foreground">Loading…</p>
            )}
            {!servicesQuery.isLoading && servicesQuery.data?.length === 0 && (
              <p className="text-sm text-muted-foreground">No services yet.</p>
            )}
            <ul className="space-y-2 text-sm">
              {servicesQuery.data?.map((service) => (
                <li key={service.id}>
                  <Link
                    href={`/organizations/${organizationId}/services/${service.id}`}
                    className="text-primary hover:underline"
                  >
                    {service.name}
                  </Link>
                  {service.teamName ? ` — ${service.teamName}` : ""}
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}
