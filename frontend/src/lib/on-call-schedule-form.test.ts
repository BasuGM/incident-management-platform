import { describe, expect, it } from "vitest";
import { serializeUpdateOnCallScheduleBody } from "@/lib/api/on-call-schedules";
import {
  addParticipant,
  applyScopeModeChange,
  buildCreateOnCallScheduleInput,
  buildUpdateOnCallScheduleBody,
  createEmptyOnCallScheduleFormValues,
  moveParticipant,
  showOnCallManagementActions,
  validateOnCallScheduleForm,
} from "@/lib/on-call-schedule-form";

const base = (): ReturnType<typeof createEmptyOnCallScheduleFormValues> => ({
  ...createEmptyOnCallScheduleFormValues(),
  name: "Primary",
  timezone: "UTC",
  handoffLocalTime: "09:00",
  handoffDayOfWeek: "1",
  effectiveFrom: "2026-01-01",
  participantUserIds: ["user-a", "user-b"],
});

describe("on-call-schedule-form validation", () => {
  it("requires weekly handoff weekday", () => {
    const values = { ...base(), handoffDayOfWeek: "" };
    expect(validateOnCallScheduleForm(values).handoffDayOfWeek).toBeTruthy();
  });

  it("does not require weekday for daily rotation", () => {
    const values = { ...base(), rotationLengthDays: 1 as const, handoffDayOfWeek: "" };
    expect(validateOnCallScheduleForm(values).handoffDayOfWeek).toBeUndefined();
  });

  it("clears handoff day in create payload for daily schedules", () => {
    const input = buildCreateOnCallScheduleInput({
      ...base(),
      rotationLengthDays: 1,
      handoffDayOfWeek: "3",
    });
    expect(input.rotationLengthDays).toBe(1);
    expect(input.handoffDayOfWeek).toBeNull();
  });

  it("prevents duplicate participants", () => {
    const errors = validateOnCallScheduleForm({
      ...base(),
      participantUserIds: ["user-a", "user-a"],
    });
    expect(errors.participants).toMatch(/once/i);
  });
});

describe("scope selection helpers", () => {
  it("clears stale scope ids when switching modes", () => {
    const start = {
      ...base(),
      scopeMode: "TEAM" as const,
      teamId: "team-1",
      serviceId: "",
    };
    const org = applyScopeModeChange(start, "ORGANIZATION");
    expect(org.teamId).toBe("");
    expect(org.serviceId).toBe("");

    const service = applyScopeModeChange(
      { ...start, serviceId: "svc-1", scopeMode: "SERVICE" },
      "SERVICE",
    );
    expect(service.teamId).toBe("");
  });

  it("sends clearScope when scope becomes organization-wide", () => {
    const initial = { ...base(), scopeMode: "TEAM" as const, teamId: "team-1" };
    const current = applyScopeModeChange(initial, "ORGANIZATION");
    const body = buildUpdateOnCallScheduleBody(initial, current);
    expect(body.clearScope).toBe(true);
    expect(body.teamId).toBeUndefined();
    expect(body.serviceId).toBeUndefined();
  });
});

describe("participant ordering", () => {
  it("rejects duplicate adds", () => {
    expect(addParticipant(["a"], "a")).toEqual({ error: expect.any(String) });
  });

  it("moves participants and preserves request order", () => {
    const ordered = moveParticipant(["a", "b", "c"], 2, "up");
    expect(ordered).toEqual(["a", "c", "b"]);
    const body = buildUpdateOnCallScheduleBody(
      { ...base(), participantUserIds: ["a", "b"] },
      { ...base(), participantUserIds: ordered },
    );
    expect(body.participantUserIds).toEqual(["a", "c", "b"]);
  });
});

describe("PATCH serialization integration", () => {
  it("omits unchanged fields in update body", () => {
    const initial = base();
    const body = buildUpdateOnCallScheduleBody(initial, { ...initial, name: "Renamed" });
    expect(serializeUpdateOnCallScheduleBody(body)).toEqual({ name: "Renamed" });
  });

  it("includes explicit clearScope without unrelated fields", () => {
    const payload = serializeUpdateOnCallScheduleBody({ clearScope: true });
    expect(payload).toEqual({ clearScope: true });
  });
});

describe("management visibility", () => {
  it("shows management actions only for managers", () => {
    expect(showOnCallManagementActions(true)).toBe(true);
    expect(showOnCallManagementActions(false)).toBe(false);
  });
});

describe("archived schedule editing", () => {
  it("does not include status changes in PATCH body", () => {
    const body = buildUpdateOnCallScheduleBody(base(), { ...base(), name: "Renamed" });
    expect(body).not.toHaveProperty("status");
    expect(Object.keys(body)).toEqual(["name"]);
  });
});

describe("form values after validation failure", () => {
  it("does not mutate values when validation fails", () => {
    const values = { ...base(), name: "" };
    const snapshot = { ...values };
    validateOnCallScheduleForm(values);
    expect(values).toEqual(snapshot);
  });
});
