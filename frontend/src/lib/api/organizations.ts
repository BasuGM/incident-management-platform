import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type { Organization, OrganizationMember, OrganizationRole } from "@/types/organization";

export async function listOrganizations(): Promise<Organization[]> {
  return getAuthenticatedClient().get<Organization[]>("/api/v1/organizations");
}

export async function createOrganization(input: { name: string; slug: string }): Promise<Organization> {
  return getAuthenticatedClient().post<Organization>("/api/v1/organizations", input);
}

export async function getOrganization(organizationId: string): Promise<Organization> {
  return getAuthenticatedClient().get<Organization>(`/api/v1/organizations/${organizationId}`);
}

export async function listOrganizationMembers(organizationId: string): Promise<OrganizationMember[]> {
  return getAuthenticatedClient().get<OrganizationMember[]>(
    `/api/v1/organizations/${organizationId}/members`,
  );
}

export async function addOrganizationMember(
  organizationId: string,
  input: { userId: string; role: OrganizationRole },
): Promise<OrganizationMember> {
  return getAuthenticatedClient().post<OrganizationMember>(
    `/api/v1/organizations/${organizationId}/members`,
    input,
  );
}
