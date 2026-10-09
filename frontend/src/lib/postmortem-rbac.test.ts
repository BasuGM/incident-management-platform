import { describe, expect, it } from "vitest";
import { getPostmortemActionVisibility } from "@/lib/postmortem-rbac";
import type { IncidentPostmortem } from "@/types/incident";

const authorId = "author-user";
const otherId = "other-user";

function draft(overrides: Partial<IncidentPostmortem> = {}): IncidentPostmortem {
  return {
    id: "pm-1",
    organizationId: "org-1",
    incidentId: "inc-1",
    authorId,
    authorEmail: "a@example.com",
    authorFirstName: "A",
    authorLastName: "Author",
    status: "DRAFT",
    title: "Title",
    summary: "s",
    impact: "",
    rootCause: "r",
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
    ...overrides,
  };
}

describe("getPostmortemActionVisibility", () => {
  it("allows MEMBER to create on resolved incident when no postmortem", () => {
    const actions = getPostmortemActionVisibility("MEMBER", undefined, authorId, true);
    expect(actions.showCreate).toBe(true);
  });

  it("blocks VIEWER from creating", () => {
    const actions = getPostmortemActionVisibility("VIEWER", undefined, authorId, true);
    expect(actions.showCreate).toBe(false);
  });

  it("allows MEMBER to edit own draft only", () => {
    const own = getPostmortemActionVisibility("MEMBER", draft(), authorId, true);
    expect(own.showEdit).toBe(true);
    expect(own.showDelete).toBe(true);

    const other = getPostmortemActionVisibility("MEMBER", draft(), otherId, true);
    expect(other.showEdit).toBe(false);
    expect(other.showDelete).toBe(false);
  });

  it("allows ADMIN to edit any draft", () => {
    const actions = getPostmortemActionVisibility("ADMIN", draft(), otherId, true);
    expect(actions.showEdit).toBe(true);
    expect(actions.showPublish).toBe(true);
  });

  it("restricts archive and unarchive to OWNER and ADMIN", () => {
    const published = draft({ status: "PUBLISHED" });
    expect(getPostmortemActionVisibility("MEMBER", published, authorId, true).showArchive).toBe(
      false,
    );
    expect(getPostmortemActionVisibility("ADMIN", published, authorId, true).showArchive).toBe(
      true,
    );

    const archived = draft({ status: "ARCHIVED" });
    expect(getPostmortemActionVisibility("MEMBER", archived, authorId, true).showUnarchive).toBe(
      false,
    );
    expect(getPostmortemActionVisibility("OWNER", archived, authorId, true).showUnarchive).toBe(
      true,
    );
  });

  it("allows MEMBER to unpublish own published postmortem", () => {
    const published = draft({ status: "PUBLISHED" });
    expect(
      getPostmortemActionVisibility("MEMBER", published, authorId, true).showUnpublish,
    ).toBe(true);
    expect(
      getPostmortemActionVisibility("MEMBER", published, otherId, true).showUnpublish,
    ).toBe(false);
  });
});
