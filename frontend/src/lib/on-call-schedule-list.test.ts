import { describe, expect, it } from "vitest";
import {
  onCallListFilterToApiStatus,
  onCallListFilterToQueryKeyStatus,
  onCallScheduleDetailPath,
  onCallSchedulesListPath,
} from "@/lib/on-call-schedule-list";
import { onCallScheduleListQueryKey } from "@/lib/hooks/use-organization-on-call-schedules";

const ORG = "org-abc";
const SCHEDULE = "sched-xyz";

describe("on-call-schedule-list", () => {
  it("uses API default for active filter", () => {
    expect(onCallListFilterToApiStatus("ACTIVE")).toBeUndefined();
    expect(onCallListFilterToQueryKeyStatus("ACTIVE")).toBe("DEFAULT");
  });

  it("passes archived and all to the API", () => {
    expect(onCallListFilterToApiStatus("ARCHIVED")).toBe("ARCHIVED");
    expect(onCallListFilterToApiStatus("ALL")).toBe("ALL");
  });

  it("builds navigation paths with organization and schedule ids", () => {
    expect(onCallSchedulesListPath(ORG)).toBe(`/organizations/${ORG}/on-call`);
    expect(onCallScheduleDetailPath(ORG, SCHEDULE)).toBe(
      `/organizations/${ORG}/on-call/${SCHEDULE}`,
    );
  });

  it("produces distinct query keys per list status filter", () => {
    expect(onCallScheduleListQueryKey(ORG, "DEFAULT", "u")).not.toEqual(
      onCallScheduleListQueryKey(ORG, "ARCHIVED", "u"),
    );
    expect(onCallScheduleListQueryKey(ORG, "ALL", "u")).not.toEqual(
      onCallScheduleListQueryKey(ORG, "DEFAULT", "u"),
    );
  });
});
