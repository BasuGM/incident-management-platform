import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/client";
import { isPostmortemForbiddenError, isPostmortemNotFoundError } from "@/lib/postmortem-errors";

describe("isPostmortemNotFoundError", () => {
  it("matches 404 POSTMORTEM_NOT_FOUND", () => {
    const error = new ApiError("Postmortem not found", 404, {
      status: 404,
      error: "POSTMORTEM_NOT_FOUND",
      message: "Postmortem not found",
      path: "/api/v1/x",
      details: [],
      timestamp: "2026-01-01T00:00:00Z",
    });
    expect(isPostmortemNotFoundError(error)).toBe(true);
  });

  it("rejects unrelated 404 codes", () => {
    const error = new ApiError("Not found", 404, {
      status: 404,
      error: "INCIDENT_NOT_FOUND",
      message: "Incident not found",
      path: "/api/v1/x",
      details: [],
      timestamp: "2026-01-01T00:00:00Z",
    });
    expect(isPostmortemNotFoundError(error)).toBe(false);
  });

  it("rejects 403", () => {
    const error = new ApiError("Forbidden", 403, {
      status: 403,
      error: "FORBIDDEN",
      message: "Forbidden",
      path: "/api/v1/x",
      details: [],
      timestamp: "2026-01-01T00:00:00Z",
    });
    expect(isPostmortemNotFoundError(error)).toBe(false);
    expect(isPostmortemForbiddenError(error)).toBe(true);
  });
});
