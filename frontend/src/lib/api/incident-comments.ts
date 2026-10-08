import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type {
  CreateIncidentCommentInput,
  IncidentComment,
  IncidentCommentPage,
  UpdateIncidentCommentInput,
} from "@/types/incident";

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

function commentsPath(organizationId: string, incidentId: string): string {
  return `/api/v1/organizations/${organizationId}/incidents/${incidentId}/comments`;
}

export async function listIncidentComments(
  organizationId: string,
  incidentId: string,
  page = DEFAULT_PAGE,
  size = DEFAULT_SIZE,
): Promise<IncidentCommentPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return getAuthenticatedClient().get<IncidentCommentPage>(
    `${commentsPath(organizationId, incidentId)}?${params.toString()}`,
  );
}

export async function createIncidentComment(
  organizationId: string,
  incidentId: string,
  input: CreateIncidentCommentInput,
): Promise<IncidentComment> {
  return getAuthenticatedClient().post<IncidentComment>(
    commentsPath(organizationId, incidentId),
    input,
  );
}

export async function updateIncidentComment(
  organizationId: string,
  incidentId: string,
  commentId: string,
  input: UpdateIncidentCommentInput,
): Promise<IncidentComment> {
  return getAuthenticatedClient().patch<IncidentComment>(
    `${commentsPath(organizationId, incidentId)}/${commentId}`,
    input,
  );
}

export async function deleteIncidentComment(
  organizationId: string,
  incidentId: string,
  commentId: string,
): Promise<void> {
  await getAuthenticatedClient().delete<void>(
    `${commentsPath(organizationId, incidentId)}/${commentId}`,
  );
}
