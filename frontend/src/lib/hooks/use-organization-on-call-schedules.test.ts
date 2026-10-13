import { describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import {
  ON_CALL_SCHEDULES_QUERY_KEY,
  invalidateOnCallScheduleQueries,
  onCallCoverageQueryKey,
  onCallScheduleDetailQueryKey,
  onCallScheduleListQueryKey,
} from "@/lib/hooks/use-organization-on-call-schedules";
import { ORGANIZATION_INCIDENTS_QUERY_KEY } from "@/lib/hooks/use-organization-incidents";

const ORG = "org-1";
const SCHEDULE = "sched-1";

describe("on-call schedule query keys", () => {
  it("isolates list by organization and status filter", () => {
    expect(onCallScheduleListQueryKey(ORG, "DEFAULT", "u")).not.toEqual(
      onCallScheduleListQueryKey("org-2", "DEFAULT", "u"),
    );
    expect(onCallScheduleListQueryKey(ORG, "ACTIVE", "u")).not.toEqual(
      onCallScheduleListQueryKey(ORG, "ARCHIVED", "u"),
    );
  });

  it("isolates detail by schedule id", () => {
    expect(onCallScheduleDetailQueryKey(ORG, SCHEDULE, "u")).not.toEqual(
      onCallScheduleDetailQueryKey(ORG, "other", "u"),
    );
  });

  it("isolates coverage by at and upcoming parameters", () => {
    expect(onCallCoverageQueryKey(ORG, SCHEDULE, "DEFAULT", "DEFAULT", "u")).not.toEqual(
      onCallCoverageQueryKey(ORG, SCHEDULE, "2026-01-01T00:00:00Z", "DEFAULT", "u"),
    );
    expect(onCallCoverageQueryKey(ORG, SCHEDULE, "DEFAULT", 0, "u")).not.toEqual(
      onCallCoverageQueryKey(ORG, SCHEDULE, "DEFAULT", 3, "u"),
    );
  });

  it("does not share root key with incidents", () => {
    expect(ON_CALL_SCHEDULES_QUERY_KEY).not.toBe(ORGANIZATION_INCIDENTS_QUERY_KEY);
  });
});

describe("on-call schedule invalidation", () => {
  it("invalidates list detail and coverage for organization scope", async () => {
    const queryClient = new QueryClient();
    const listKey = onCallScheduleListQueryKey(ORG, "DEFAULT", "user");
    const detailKey = onCallScheduleDetailQueryKey(ORG, SCHEDULE, "user");
    const coverageKey = onCallCoverageQueryKey(ORG, SCHEDULE, "DEFAULT", 3, "user");
    const otherOrgList = onCallScheduleListQueryKey("other", "DEFAULT", "user");

    queryClient.setQueryData(listKey, []);
    queryClient.setQueryData(detailKey, { schedule: {}, participants: [] });
    queryClient.setQueryData(coverageKey, { current: null });
    queryClient.setQueryData(otherOrgList, []);

    await invalidateOnCallScheduleQueries(queryClient, ORG, SCHEDULE);

    expect(queryClient.getQueryState(listKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(detailKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(coverageKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(otherOrgList)?.isInvalidated).toBe(false);
  });

  it("does not invalidate incident queries", async () => {
    const queryClient = new QueryClient();
    const incidentKey = [ORGANIZATION_INCIDENTS_QUERY_KEY, ORG, {}, "user"];
    queryClient.setQueryData(incidentKey, { content: [] });

    await invalidateOnCallScheduleQueries(queryClient, ORG, SCHEDULE);

    expect(queryClient.getQueryState(incidentKey)?.isInvalidated).toBeFalsy();
  });
});
