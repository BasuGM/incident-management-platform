import { describe, expect, it } from "vitest";
import {
  clampPostmortemLibraryPage,
  clampPostmortemLibraryPageSize,
  getPostmortemLibraryEmptyMessage,
  getPostmortemLibraryFilterOptions,
  incidentDetailPath,
  libraryStatusFilterToQueryStatus,
  summarizePostmortemText,
} from "@/lib/organization-postmortems-library";
import type { IncidentPostmortem } from "@/types/incident";

const ORG = "org-1";
const INC = "inc-1";

const sample: IncidentPostmortem = {
  id: "pm-1",
  organizationId: ORG,
  incidentId: INC,
  authorId: "u1",
  authorEmail: "a@example.com",
  authorFirstName: "Ada",
  authorLastName: "Author",
  status: "PUBLISHED",
  title: "Postmortem: API outage",
  summary: "  Hello <script>alert(1)</script> world  ",
  impact: "",
  rootCause: "x",
  resolution: "",
  lessonsLearned: "",
  correctiveActions: "",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
  publishedAt: "2026-01-03T00:00:00Z",
  publishedById: "pub-1",
  publishedByEmail: "pub@example.com",
  publishedByFirstName: "Pub",
  publishedByLastName: "Lisher",
  archivedAt: null,
};

describe("libraryStatusFilterToQueryStatus", () => {
  it("omits status for default published view", () => {
    expect(libraryStatusFilterToQueryStatus("DEFAULT_PUBLISHED")).toBeUndefined();
  });

  it("maps explicit filters", () => {
    expect(libraryStatusFilterToQueryStatus("DRAFT")).toBe("DRAFT");
    expect(libraryStatusFilterToQueryStatus("ALL")).toBe("ALL");
  });
});

describe("pagination helpers", () => {
  it("clamps out-of-range pages", () => {
    expect(clampPostmortemLibraryPage(9, 3)).toBe(2);
    expect(clampPostmortemLibraryPage(-1, 3)).toBe(0);
    expect(clampPostmortemLibraryPage(1, 0)).toBe(0);
  });

  it("clamps page size to backend max", () => {
    expect(clampPostmortemLibraryPageSize(500)).toBe(100);
    expect(clampPostmortemLibraryPageSize(0)).toBe(1);
  });
});

describe("getPostmortemLibraryFilterOptions", () => {
  it("exposes all filters for members and viewers (membership-only backend rule)", () => {
    expect(getPostmortemLibraryFilterOptions("VIEWER")).toHaveLength(4);
    expect(getPostmortemLibraryFilterOptions("MEMBER").map((o) => o.value)).toContain("DRAFT");
    expect(getPostmortemLibraryFilterOptions("ADMIN").map((o) => o.value)).toContain("ALL");
  });
});

describe("empty messages", () => {
  it("distinguishes published default from other filters", () => {
    expect(getPostmortemLibraryEmptyMessage("DEFAULT_PUBLISHED")).toMatch(/published/i);
    expect(getPostmortemLibraryEmptyMessage("DRAFT")).toMatch(/draft/i);
  });
});

describe("summarizePostmortemText", () => {
  it("trims and truncates without HTML interpretation", () => {
    expect(summarizePostmortemText(sample.summary, 20)).toBe("Hello <script>alert…");
    expect(summarizePostmortemText("   ")).toBe("");
  });
});

describe("incidentDetailPath", () => {
  it("builds organization-scoped incident URLs", () => {
    expect(incidentDetailPath(ORG, INC)).toBe(`/organizations/${ORG}/incidents/${INC}`);
  });
});
