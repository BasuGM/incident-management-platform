import { describe, expect, it } from "vitest";
import {
  buildPostmortemUpdateBody,
  getPublishValidationError,
  isPostmortemMutationPending,
  validatePostmortemForm,
} from "@/lib/postmortem-form";
import { POSTMORTEM_SECTION_MAX_LENGTH, POSTMORTEM_TITLE_MAX_LENGTH } from "@/lib/postmortem-rbac";
import type { IncidentPostmortem } from "@/types/incident";

const current: IncidentPostmortem = {
  id: "pm-1",
  organizationId: "org-1",
  incidentId: "inc-1",
  authorId: "user-1",
  authorEmail: "a@example.com",
  authorFirstName: "A",
  authorLastName: "Author",
  status: "DRAFT",
  title: "Old title",
  summary: "Old summary",
  impact: "",
  rootCause: "cause",
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

describe("getPublishValidationError", () => {
  it("requires summary and root cause", () => {
    expect(getPublishValidationError({ summary: "", rootCause: "x" })).toMatch(/Summary/);
    expect(getPublishValidationError({ summary: "x", rootCause: "   " })).toMatch(/Root cause/);
    expect(getPublishValidationError({ summary: "x", rootCause: "y" })).toBeNull();
  });
});

describe("validatePostmortemForm", () => {
  it("enforces field limits", () => {
    expect(
      validatePostmortemForm({
        title: "x".repeat(POSTMORTEM_TITLE_MAX_LENGTH + 1),
        summary: "",
        impact: "",
        rootCause: "",
        resolution: "",
        lessonsLearned: "",
        correctiveActions: "",
      }),
    ).toMatch(/Title/);

    expect(
      validatePostmortemForm({
        title: "ok",
        summary: "x".repeat(POSTMORTEM_SECTION_MAX_LENGTH + 1),
        impact: "",
        rootCause: "",
        resolution: "",
        lessonsLearned: "",
        correctiveActions: "",
      }),
    ).toMatch(/section/);
  });
});

describe("buildPostmortemUpdateBody", () => {
  it("sends only changed fields and null to clear", () => {
    expect(
      buildPostmortemUpdateBody(current, {
        title: "New title",
        summary: "",
        impact: "",
        rootCause: "cause",
        resolution: "",
        lessonsLearned: "",
        correctiveActions: "",
      }),
    ).toEqual({
      title: "New title",
      summary: null,
    });
  });
});

describe("isPostmortemMutationPending", () => {
  it("returns true when any flag is set", () => {
    expect(isPostmortemMutationPending({ publish: true })).toBe(true);
    expect(isPostmortemMutationPending({})).toBe(false);
  });
});
