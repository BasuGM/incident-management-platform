import { ApiError } from "@/lib/api/client";
import { getApiErrorBody } from "@/lib/api/errors";

export function isPostmortemNotFoundError(error: unknown): boolean {
  if (!(error instanceof ApiError) || error.status !== 404) {
    return false;
  }
  const body = getApiErrorBody(error);
  return body?.error === "POSTMORTEM_NOT_FOUND";
}

export function isPostmortemForbiddenError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}
