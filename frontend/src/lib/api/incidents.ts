import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import { buildIncidentListQueryString, type IncidentListApiQuery } from "@/lib/incident-list-filters";
import type {
  CreateIncidentInput,
  Incident,
  IncidentEventPage,
  IncidentPage,
  UpdateIncidentBody,
} from "@/types/incident";

export type ListIncidentsParams = IncidentListApiQuery;

export function serializeListIncidentsParams(params: ListIncidentsParams): string {
  return buildIncidentListQueryString(params);
}

export async function listIncidents(
  organizationId: string,
  params: ListIncidentsParams,
): Promise<IncidentPage> {
  const queryString = buildIncidentListQueryString({
    page: params.page ?? 0,
    size: params.size ?? 20,
    active: params.active,
    status: params.status,
    severity: params.severity,
    serviceId: params.serviceId,
    commanderId: params.commanderId,
    mine: params.mine,
    search: params.search,
  });

  return getAuthenticatedClient().get<IncidentPage>(
    `/api/v1/organizations/${organizationId}/incidents?${queryString}`,
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

export async function getIncidentEvents(
  organizationId: string,
  incidentId: string,
  page = 0,
  size = 20,
): Promise<IncidentEventPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return getAuthenticatedClient().get<IncidentEventPage>(
    `/api/v1/organizations/${organizationId}/incidents/${incidentId}/events?${params.toString()}`,
  );
}
