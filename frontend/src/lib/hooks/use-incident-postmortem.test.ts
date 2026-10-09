import { describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import {
  INCIDENT_POSTMORTEM_QUERY_KEY,
  ORGANIZATION_POSTMORTEMS_QUERY_KEY,
  incidentPostmortemQueryKey,
  invalidateIncidentPostmortemQueries,
  invalidateOrganizationPostmortemListQueries,
  organizationPostmortemsQueryKey,
} from "@/lib/hooks/use-incident-postmortem";

const ORG = "org-1";
const INC = "inc-1";

describe("postmortem query keys", () => {
  it("isolates incident postmortem by organization and incident", () => {
    expect(incidentPostmortemQueryKey(ORG, INC, "user-a")).not.toEqual(
      incidentPostmortemQueryKey("org-2", INC, "user-a"),
    );
    expect(incidentPostmortemQueryKey(ORG, INC, "user-a")).not.toEqual(
      incidentPostmortemQueryKey(ORG, "inc-2", "user-a"),
    );
  });

  it("isolates organization lists by status filter", () => {
    expect(organizationPostmortemsQueryKey(ORG, 0, 20, "PUBLISHED", "u")).not.toEqual(
      organizationPostmortemsQueryKey(ORG, 0, 20, "DRAFT", "u"),
    );
    expect(organizationPostmortemsQueryKey(ORG, 0, 20, "DEFAULT", "u")).not.toEqual(
      organizationPostmortemsQueryKey(ORG, 0, 20, "ALL", "u"),
    );
  });

  it("does not share keys with incident comments or events", () => {
    const postmortemKey = incidentPostmortemQueryKey(ORG, INC, "u")[0];
    expect(postmortemKey).not.toBe("incident-comments");
    expect(postmortemKey).not.toBe("incident-events");
  });
});

describe("postmortem query invalidation", () => {
  it("invalidates incident postmortem queries for the scoped incident", async () => {
    const queryClient = new QueryClient();
    const key = incidentPostmortemQueryKey(ORG, INC, "user");
    queryClient.setQueryData(key, { id: "pm" });

    await invalidateIncidentPostmortemQueries(queryClient, ORG, INC);

    expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
  });

  it("invalidates organization list queries without touching unrelated org", async () => {
    const queryClient = new QueryClient();
    const orgKey = organizationPostmortemsQueryKey(ORG, 0, 20, "PUBLISHED", "user");
    const otherOrgKey = organizationPostmortemsQueryKey("other", 0, 20, "PUBLISHED", "user");
    queryClient.setQueryData(orgKey, { content: [] });
    queryClient.setQueryData(otherOrgKey, { content: [] });

    await invalidateOrganizationPostmortemListQueries(queryClient, ORG);

    expect(queryClient.getQueryState(orgKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(otherOrgKey)?.isInvalidated).toBe(false);
  });

  it("uses distinct root keys for singleton and list", () => {
    expect(INCIDENT_POSTMORTEM_QUERY_KEY).not.toBe(ORGANIZATION_POSTMORTEMS_QUERY_KEY);
  });
});

describe("create postmortem cache", () => {
  it("setQueryData populates incident postmortem key after create", () => {
    const queryClient = new QueryClient();
    const key = incidentPostmortemQueryKey(ORG, INC, "user");
    const created = { id: "pm-new", status: "DRAFT" as const };
    queryClient.setQueryData(key, created);
    expect(queryClient.getQueryData(key)).toEqual(created);
  });
});
