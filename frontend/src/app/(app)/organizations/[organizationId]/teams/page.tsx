"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getOrganization } from "@/lib/api/organizations";
import { createTeam, listTeams } from "@/lib/api/teams";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function OrganizationTeamsPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const queryClient = useQueryClient();
  const [teamName, setTeamName] = useState("");
  const [teamDescription, setTeamDescription] = useState("");

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
  });

  const createTeamMutation = useMutation({
    mutationFn: () =>
      createTeam(organizationId, {
        name: teamName.trim(),
        description: teamDescription.trim() || undefined,
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
    <div className="space-y-6">
      <Link href={`/organizations/${organizationId}`} className="text-sm text-primary hover:underline">
        ← Back to organization
      </Link>
      <h1 className="text-3xl font-semibold tracking-tight">Teams</h1>

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

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

      {teamsQuery.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
      <ul className="space-y-2 text-sm">
        {teamsQuery.data?.map((team) => (
          <li key={team.id} className="rounded border px-3 py-2">
            <Link
              href={`/organizations/${organizationId}/teams/${team.id}`}
              className="font-medium text-primary hover:underline"
            >
              {team.name}
            </Link>
            {team.description ? <p className="text-muted-foreground">{team.description}</p> : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
