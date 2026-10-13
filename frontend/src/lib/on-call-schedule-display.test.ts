import { describe, expect, it } from "vitest";
import {
  formatInstantInTimeZone,
  formatOnCallCadence,
  formatOnCallScopeLabel,
  onCallScopeKind,
  sortOnCallParticipantsByPosition,
} from "@/lib/on-call-schedule-display";

describe("on-call-schedule-display", () => {
  it("labels organization, team, and service scope", () => {
    expect(onCallScopeKind(null, null)).toBe("ORGANIZATION");
    expect(formatOnCallScopeLabel({ teamId: null, serviceId: null })).toBe("Organization-wide");

    expect(
      formatOnCallScopeLabel(
        { teamId: "t1", serviceId: null },
        { teamNames: new Map([["t1", "Platform"]]) },
      ),
    ).toBe("Team · Platform");

    expect(
      formatOnCallScopeLabel(
        { teamId: null, serviceId: "s1" },
        { serviceNames: new Map([["s1", "Payments"]]) },
      ),
    ).toBe("Service · Payments");
  });

  it("renders daily and weekly cadence", () => {
    expect(
      formatOnCallCadence({
        rotationLengthDays: 1,
        handoffLocalTime: "09:00:00",
        handoffDayOfWeek: null,
      }),
    ).toBe("Daily at 09:00");

    expect(
      formatOnCallCadence({
        rotationLengthDays: 7,
        handoffLocalTime: "09:30:00",
        handoffDayOfWeek: 1,
      }),
    ).toBe("Weekly · Monday at 09:30");
  });

  it("sorts participants by position", () => {
    const sorted = sortOnCallParticipantsByPosition([
      { userId: "b", displayName: "B", position: 2 },
      { userId: "a", displayName: "A", position: 1 },
    ]);
    expect(sorted.map((p) => p.displayName)).toEqual(["A", "B"]);
  });

  it("returns the original value when instant formatting fails", () => {
    expect(formatInstantInTimeZone("not-an-instant", "UTC")).toBe("not-an-instant");
  });
});
