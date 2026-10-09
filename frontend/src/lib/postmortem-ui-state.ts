import { getApiErrorMessage } from "@/lib/api/errors";
import { isPostmortemNotFoundError } from "@/lib/postmortem-errors";
import { canCreatePostmortem } from "@/lib/postmortem-rbac";
import type { IncidentPostmortem } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";

export type PostmortemViewState =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "empty"; canCreate: boolean; hint: string }
  | { kind: "content"; postmortem: IncidentPostmortem };

export function resolvePostmortemViewState(params: {
  isPending: boolean;
  isFetching: boolean;
  error: unknown | null;
  data?: IncidentPostmortem;
  role: OrganizationRole;
  incidentResolved: boolean;
}): PostmortemViewState {
  const { isPending, error, data, role, incidentResolved } = params;

  if (isPending && !data) {
    return { kind: "loading" };
  }

  if (data) {
    return { kind: "content", postmortem: data };
  }

  if (error && isPostmortemNotFoundError(error)) {
    const canCreate = canCreatePostmortem(role, incidentResolved);
    const hint = incidentResolved
      ? canCreate
        ? "No postmortem has been written for this incident yet."
        : "You do not have permission to create a postmortem."
      : "A postmortem can be created after the incident is resolved.";
    return { kind: "empty", canCreate, hint };
  }

  if (error) {
    return {
      kind: "error",
      message: getApiErrorMessage(error, "Failed to load postmortem"),
    };
  }

  return {
    kind: "empty",
    canCreate: canCreatePostmortem(role, incidentResolved),
    hint: incidentResolved
      ? "No postmortem has been written for this incident yet."
      : "A postmortem can be created after the incident is resolved.",
  };
}
