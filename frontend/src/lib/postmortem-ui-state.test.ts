import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/client";
import { resolvePostmortemViewState } from "@/lib/postmortem-ui-state";
import type { IncidentPostmortem } from "@/types/incident";

const basePostmortem: IncidentPostmortem = {
  id: "pm-1",
  organizationId: "org-1",
  incidentId: "inc-1",
  authorId: "user-1",
  authorEmail: "a@example.com",
  authorFirstName: "A",
  authorLastName: "Author",
  status: "DRAFT",
  title: "Postmortem",
  summary: "",
  impact: "",
  rootCause: "",
  resolution: "",
  lessonsLearned: "",
  correctiveActions: "",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  publishedAt: null,
  publishedById: null,
  publishedByEmail: null,
  publishedByFirstName: null,
  publishedByLastName: null,
  archivedAt: null,
};

function notFoundError() {
  return new ApiError("Postmortem not found", 404, {
    status: 404,
    error: "POSTMORTEM_NOT_FOUND",
    message: "Postmortem not found",
    path: "/api/v1/x",
    details: [],
    timestamp: "2026-01-01T00:00:00Z",
  });
}

describe("resolvePostmortemViewState", () => {
  it("maps POSTMORTEM_NOT_FOUND to empty state with create for MEMBER on resolved incident", () => {
    const state = resolvePostmortemViewState({
      isPending: false,
      isFetching: false,
      error: notFoundError(),
      role: "MEMBER",
      incidentResolved: true,
    });
    expect(state).toEqual({
      kind: "empty",
      canCreate: true,
      hint: "No postmortem has been written for this incident yet.",
    });
  });

  it("maps unrelated 404 to error state", () => {
    const error = new ApiError("Not found", 404, {
      status: 404,
      error: "INCIDENT_NOT_FOUND",
      message: "Incident not found",
      path: "/api/v1/x",
      details: [],
      timestamp: "2026-01-01T00:00:00Z",
    });
    const state = resolvePostmortemViewState({
      isPending: false,
      isFetching: false,
      error,
      role: "MEMBER",
      incidentResolved: true,
    });
    expect(state.kind).toBe("error");
  });

  it("maps 403 to error state", () => {
    const error = new ApiError("Forbidden", 403, {
      status: 403,
      error: "FORBIDDEN",
      message: "Forbidden",
      path: "/api/v1/x",
      details: [],
      timestamp: "2026-01-01T00:00:00Z",
    });
    const state = resolvePostmortemViewState({
      isPending: false,
      isFetching: false,
      error,
      role: "VIEWER",
      incidentResolved: true,
    });
    expect(state.kind).toBe("error");
  });

  it("returns content when data is present", () => {
    const state = resolvePostmortemViewState({
      isPending: false,
      isFetching: false,
      error: null,
      data: basePostmortem,
      role: "VIEWER",
      incidentResolved: true,
    });
    expect(state).toEqual({ kind: "content", postmortem: basePostmortem });
  });

  it("prefers cached data over a stale POSTMORTEM_NOT_FOUND error", () => {
    const state = resolvePostmortemViewState({
      isPending: false,
      isFetching: false,
      error: notFoundError(),
      data: basePostmortem,
      role: "MEMBER",
      incidentResolved: true,
    });
    expect(state).toEqual({ kind: "content", postmortem: basePostmortem });
  });
});
