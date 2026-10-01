"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getOrganization } from "@/lib/api/organizations";
import { addTeamMember, getTeam, listTeamMembers } from "@/lib/api/teams";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function TeamDetailPage() {
  const params = useParams<{ organizationId: string; teamId: string }>();
  const { organizationId, teamId } = params;
  const queryClient = useQueryClient();
  const [userId, setUserId] = useState("");

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const teamQuery = useQuery({
    queryKey: ["team", organizationId, teamId],
    queryFn: () => getTeam(organizationId, teamId),
  });

  const membersQuery = useQuery({
    queryKey: ["team-members", teamId],
    queryFn: () => listTeamMembers(teamId),
  });

  const addMemberMutation = useMutation({
    mutationFn: () => addTeamMember(teamId, userId),
    onSuccess: async () => {
      setUserId("");
      await queryClient.invalidateQueries({ queryKey: ["team-members", teamId] });
    },
  });

  const canManageTeam =
    organizationQuery.data?.currentUserRole === "OWNER" ||
    organizationQuery.data?.currentUserRole === "ADMIN";

  return (
    <div className="space-y-8">
      <Link
        href={`/organizations/${organizationId}`}
        className="text-sm text-primary hover:underline"
      >
        ← Back to organization
      </Link>

      {teamQuery.data && (
        <section className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight">{teamQuery.data.name}</h1>
          {teamQuery.data.description && (
            <p className="text-sm text-muted-foreground">{teamQuery.data.description}</p>
          )}
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-base font-semibold">Team members</h2>
        {membersQuery.isPending && <p className="text-sm text-muted-foreground">Loading…</p>}
        <ul className="space-y-2 text-sm">
          {membersQuery.data?.map((member) => (
            <li key={member.id} className="rounded border px-3 py-2">
              {member.firstName} {member.lastName} ({member.email})
            </li>
          ))}
        </ul>
      </section>

      {canManageTeam ? (
        <form
          className="max-w-md space-y-3 rounded-lg border p-4"
          onSubmit={(event) => {
            event.preventDefault();
            addMemberMutation.mutate();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="member-user-id">User ID</Label>
            <Input
              id="member-user-id"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
              required
            />
          </div>
          <Button type="submit" disabled={addMemberMutation.isPending}>
            Add team member
          </Button>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">
          Team membership management requires organization OWNER or ADMIN.
        </p>
      )}
    </div>
  );
}
