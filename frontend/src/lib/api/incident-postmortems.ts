import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type {
  CreateIncidentPostmortemInput,
  IncidentPostmortem,
  IncidentPostmortemPage,
  OrganizationPostmortemListStatus,
  UpdateIncidentPostmortemBody,
} from "@/types/incident";

export const DEFAULT_POSTMORTEM_PAGE = 0;
export const DEFAULT_POSTMORTEM_PAGE_SIZE = 20;
export const MAX_POSTMORTEM_PAGE_SIZE = 100;

export type ListOrganizationPostmortemsParams = {
  page?: number;
  size?: number;
  /** Omit to use backend default (`PUBLISHED`). */
  status?: OrganizationPostmortemListStatus;
};

export function incidentPostmortemPath(organizationId: string, incidentId: string): string {
  return `/api/v1/organizations/${organizationId}/incidents/${incidentId}/postmortem`;
}

export function organizationPostmortemsPath(
  organizationId: string,
  params: ListOrganizationPostmortemsParams = {},
): string {
  const page = params.page ?? DEFAULT_POSTMORTEM_PAGE;
  const size = Math.min(Math.max(params.size ?? DEFAULT_POSTMORTEM_PAGE_SIZE, 1), MAX_POSTMORTEM_PAGE_SIZE);
  const search = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (params.status !== undefined) {
    search.set("status", params.status);
  }
  return `/api/v1/organizations/${organizationId}/postmortems?${search.toString()}`;
}

/** Serializes PATCH body; preserves explicit `null` values for nullable sections. */
export function serializeUpdateIncidentPostmortemBody(
  body: UpdateIncidentPostmortemBody,
): Record<string, string | null> {
  const payload: Record<string, string | null> = {};
  for (const key of [
    "title",
    "summary",
    "impact",
    "rootCause",
    "resolution",
    "lessonsLearned",
    "correctiveActions",
  ] as const) {
    if (Object.prototype.hasOwnProperty.call(body, key)) {
      payload[key] = body[key] ?? null;
    }
  }
  return payload;
}

export async function getIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().get<IncidentPostmortem>(
    incidentPostmortemPath(organizationId, incidentId),
  );
}

export async function createIncidentPostmortem(
  organizationId: string,
  incidentId: string,
  input?: CreateIncidentPostmortemInput,
): Promise<IncidentPostmortem> {
  const path = incidentPostmortemPath(organizationId, incidentId);
  if (input === undefined || Object.keys(input).length === 0) {
    return getAuthenticatedClient().post<IncidentPostmortem>(path);
  }
  return getAuthenticatedClient().post<IncidentPostmortem>(path, input);
}

export async function updateIncidentPostmortem(
  organizationId: string,
  incidentId: string,
  body: UpdateIncidentPostmortemBody,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().patch<IncidentPostmortem>(
    incidentPostmortemPath(organizationId, incidentId),
    serializeUpdateIncidentPostmortemBody(body),
  );
}

export async function deleteIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<void> {
  await getAuthenticatedClient().delete<void>(incidentPostmortemPath(organizationId, incidentId));
}

export async function publishIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().post<IncidentPostmortem>(
    `${incidentPostmortemPath(organizationId, incidentId)}/publish`,
  );
}

export async function unpublishIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().post<IncidentPostmortem>(
    `${incidentPostmortemPath(organizationId, incidentId)}/unpublish`,
  );
}

export async function archiveIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().post<IncidentPostmortem>(
    `${incidentPostmortemPath(organizationId, incidentId)}/archive`,
  );
}

export async function unarchiveIncidentPostmortem(
  organizationId: string,
  incidentId: string,
): Promise<IncidentPostmortem> {
  return getAuthenticatedClient().post<IncidentPostmortem>(
    `${incidentPostmortemPath(organizationId, incidentId)}/unarchive`,
  );
}

export async function listOrganizationPostmortems(
  organizationId: string,
  params: ListOrganizationPostmortemsParams = {},
): Promise<IncidentPostmortemPage> {
  return getAuthenticatedClient().get<IncidentPostmortemPage>(
    organizationPostmortemsPath(organizationId, params),
  );
}
