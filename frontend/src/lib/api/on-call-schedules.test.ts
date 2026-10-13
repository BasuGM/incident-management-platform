import { describe, expect, it } from "vitest";
import {
  listOnCallSchedulesPath,
  onCallCoveragePath,
  onCallSchedulePath,
  onCallSchedulesBasePath,
  serializeUpdateOnCallScheduleBody,
} from "@/lib/api/on-call-schedules";

const ORG = "11111111-1111-1111-1111-111111111111";
const SCHEDULE = "22222222-2222-2222-2222-222222222222";

describe("on-call-schedules paths", () => {
  it("builds base and schedule paths", () => {
    expect(onCallSchedulesBasePath(ORG)).toBe(`/api/v1/organizations/${ORG}/on-call-schedules`);
    expect(onCallSchedulePath(ORG, SCHEDULE)).toBe(
      `/api/v1/organizations/${ORG}/on-call-schedules/${SCHEDULE}`,
    );
  });

  it("omits status query when listing with default", () => {
    expect(listOnCallSchedulesPath(ORG)).toBe(onCallSchedulesBasePath(ORG));
  });

  it("serializes list status including ALL", () => {
    expect(listOnCallSchedulesPath(ORG, { status: "ARCHIVED" })).toContain("status=ARCHIVED");
    expect(listOnCallSchedulesPath(ORG, { status: "ALL" })).toContain("status=ALL");
  });

  it("serializes coverage at and upcoming", () => {
    const path = onCallCoveragePath(ORG, SCHEDULE, {
      at: "2026-06-01T09:00:00Z",
      upcoming: 5,
    });
    expect(path).toContain("/coverage?");
    expect(path).toContain("at=2026-06-01T09%3A00%3A00Z");
    expect(path).toContain("upcoming=5");
  });

  it("preserves upcoming=0", () => {
    expect(onCallCoveragePath(ORG, SCHEDULE, { upcoming: 0 })).toContain("upcoming=0");
  });

  it("omits at when not provided", () => {
    expect(onCallCoveragePath(ORG, SCHEDULE, {})).toBe(
      `${onCallSchedulePath(ORG, SCHEDULE)}/coverage`,
    );
  });
});

describe("serializeUpdateOnCallScheduleBody", () => {
  it("omits unset fields", () => {
    expect(serializeUpdateOnCallScheduleBody({ name: "New" })).toEqual({ name: "New" });
  });

  it("includes participant replacement and clearScope", () => {
    expect(
      serializeUpdateOnCallScheduleBody({
        clearScope: true,
        participantUserIds: ["a", "b"],
      }),
    ).toEqual({
      clearScope: true,
      participantUserIds: ["a", "b"],
    });
  });
});

describe("on-call schedule detail shape", () => {
  it("expects nested schedule and participants", () => {
    const detail = {
      schedule: { id: SCHEDULE, name: "Primary", status: "ACTIVE" as const },
      participants: [{ userId: "u1", displayName: "User One", position: 0 }],
    };
    expect(detail.schedule.id).toBe(SCHEDULE);
    expect(detail.participants[0].position).toBe(0);
  });
});
