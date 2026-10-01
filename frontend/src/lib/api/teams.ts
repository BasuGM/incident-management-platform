import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type { Team, TeamMember } from "@/types/organization";

export async function listTeams(organizationId: string): Promise<Team[]> {
  return getAuthenticatedClient().get<Team[]>(`/api/v1/organizations/${organizationId}/teams`);
}

export async function createTeam(
  organizationId: string,
  input: { name: string; description?: string },
): Promise<Team> {
  return getAuthenticatedClient().post<Team>(`/api/v1/organizations/${organizationId}/teams`, input);
}

export async function getTeam(organizationId: string, teamId: string): Promise<Team> {
  return getAuthenticatedClient().get<Team>(
    `/api/v1/organizations/${organizationId}/teams/${teamId}`,
  );
}

export async function listTeamMembers(teamId: string): Promise<TeamMember[]> {
  return getAuthenticatedClient().get<TeamMember[]>(`/api/v1/teams/${teamId}/members`);
}

export async function addTeamMember(teamId: string, userId: string): Promise<TeamMember> {
  return getAuthenticatedClient().post<TeamMember>(`/api/v1/teams/${teamId}/members`, { userId });
}
