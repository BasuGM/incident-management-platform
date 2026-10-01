"use client";

import { listTeams } from "@/lib/api/teams";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";

export default function OrganizationTeamsPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
  });

  return (
    <div className="space-y-6">
      <div>
        <Link href={`/organizations/${organizationId}`} className="text-sm text-primary hover:underline">
          ← Back to organization
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Teams</h1>
      </div>
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
