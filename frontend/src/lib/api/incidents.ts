import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type {
  CreateIncidentInput,
  Incident,
  IncidentPage,
  UpdateIncidentBody,
} from "@/types/incident";

export async function listIncidents(
  organizationId: string,
  page = 0,
  size = 20,
): Promise<IncidentPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return getAuthenticatedClient().get<IncidentPage>(
    `/api/v1/organizations/${organizationId}/incidents?${params.toString()}`,
  );
}

export async function getIncident(organizationId: string, incidentId: string): Promise<Incident> {
  return getAuthenticatedClient().get<Incident>(
    `/api/v1/organizations/${organizationId}/incidents/${incidentId}`,
  );
}

export async function createIncident(
  organizationId: string,
  input: CreateIncidentInput,
): Promise<Incident> {
  return getAuthenticatedClient().post<Incident>(
    `/api/v1/organizations/${organizationId}/incidents`,
    input,
  );
}

export async function updateIncident(
  organizationId: string,
  incidentId: string,
  body: UpdateIncidentBody,
): Promise<Incident> {
  return getAuthenticatedClient().patch<Incident>(
    `/api/v1/organizations/${organizationId}/incidents/${incidentId}`,
    body,
  );
}
