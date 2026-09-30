import { ApiError } from "@/lib/api/client";
import type { ApiErrorResponse } from "@/types/api";

export function getApiErrorBody(error: unknown): ApiErrorResponse | undefined {
  if (error instanceof ApiError && error.body && typeof error.body === "object") {
    return error.body as ApiErrorResponse;
  }
  return undefined;
}

export function getApiErrorMessage(error: unknown, fallback = "Something went wrong"): string {
  const body = getApiErrorBody(error);
  if (body?.message) {
    return body.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

export function getApiErrorDetails(error: unknown): string[] {
  return getApiErrorBody(error)?.details ?? [];
}
