import { describe, expect, it } from "vitest";
import {
  buildIncidentListQueryString,
  clearIncidentListFilters,
  defaultIncidentListFilters,
  incidentListFiltersQueryKeyPayload,
  incidentListFiltersToApiQuery,
  normalizeIncidentListFilters,
  parseIncidentListFilters,
  serializeIncidentListSearchParams,
  shouldApplyDefaultActiveView,
  withIncidentListFilterChange,
} from "@/lib/incident-list-filters";
import { organizationIncidentsQueryKey } from "@/lib/hooks/use-organization-incidents";

describe("incident-list-filters", () => {
  it("applies default active view when URL has no discovery params", () => {
    const params = new URLSearchParams();
    expect(shouldApplyDefaultActiveView(params)).toBe(true);
    const filters = parseIncidentListFilters(params);
    expect(filters.active).toBe(true);
    expect(serializeIncidentListSearchParams(filters)).toBe("active=true");
  });

  it("serializes explicit active=false", () => {
    const qs = serializeIncidentListSearchParams({
      page: 0,
      size: 20,
      active: false,
    });
    expect(qs).toBe("active=false");
    const api = buildIncidentListQueryString(incidentListFiltersToApiQuery({
      page: 0,
      size: 20,
      active: false,
    }));
    expect(api).toContain("active=false");
  });

  it("status takes precedence over active in API query", () => {
    const api = incidentListFiltersToApiQuery(
      normalizeIncidentListFilters({
        page: 0,
        size: 20,
        active: true,
        status: "RESOLVED",
      }),
    );
    expect(api.status).toBe("RESOLVED");
    expect(api.active).toBeUndefined();
  });

  it("resolves mine versus commanderId conflict by dropping commanderId", () => {
    const params = new URLSearchParams({
      mine: "true",
      commanderId: "00000000-0000-0000-0000-000000000099",
    });
    const filters = parseIncidentListFilters(params);
    expect(filters.mine).toBe(true);
    expect(filters.commanderId).toBeUndefined();
    const api = incidentListFiltersToApiQuery(filters);
    expect(api.mine).toBe(true);
    expect(api.commanderId).toBeUndefined();
  });

  it("resets page when filters change", () => {
    const next = withIncidentListFilterChange(
      { page: 3, size: 20, active: true },
      { severity: "SEV1" },
    );
    expect(next.page).toBe(0);
    expect(next.severity).toBe("SEV1");
  });

  it("clear filters restores default active view", () => {
    const cleared = clearIncidentListFilters();
    expect(cleared.active).toBe(true);
    expect(cleared.page).toBe(0);
    expect(serializeIncidentListSearchParams(cleared)).toBe("active=true");
  });

  it("omits blank search from serialization and API query", () => {
    const filters = withIncidentListFilterChange(defaultIncidentListFilters(), {
      search: "   ",
    });
    expect(filters.search).toBeUndefined();
    expect(serializeIncidentListSearchParams(filters)).toBe("active=true");
  });

  it("isolates query keys by organization and filter payload", () => {
    const orgA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    const orgB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    const user = "user-1";
    const active = defaultIncidentListFilters();
    const all = withIncidentListFilterChange(active, { active: undefined, status: undefined });
    all.active = undefined;

    const keyA = organizationIncidentsQueryKey(orgA, active, user);
    const keyB = organizationIncidentsQueryKey(orgB, active, user);
    const keyFiltered = organizationIncidentsQueryKey(
      orgA,
      withIncidentListFilterChange(active, { severity: "SEV2" }),
      user,
    );

    expect(keyA).not.toEqual(keyB);
    expect(keyA).not.toEqual(keyFiltered);
    expect(incidentListFiltersQueryKeyPayload(active)).not.toEqual(
      incidentListFiltersQueryKeyPayload(
        withIncidentListFilterChange(active, { severity: "SEV2" }),
      ),
    );
  });
});
