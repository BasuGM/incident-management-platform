import type {
  CreateOnCallScheduleInput,
  OnCallScheduleDetail,
  UpdateOnCallScheduleBody,
} from "@/types/on-call-schedule";
import { formatOnCallHandoffLocalTime, onCallScopeKind } from "@/lib/on-call-schedule-display";

export type OnCallScopeMode = "ORGANIZATION" | "TEAM" | "SERVICE";

export type OnCallScheduleFormValues = {
  name: string;
  rotationLengthDays: 1 | 7;
  timezone: string;
  /** HTML time input value (`HH:mm`). */
  handoffLocalTime: string;
  /** ISO weekday 1–7 as string, or empty when not applicable. */
  handoffDayOfWeek: string;
  effectiveFrom: string;
  scopeMode: OnCallScopeMode;
  teamId: string;
  serviceId: string;
  participantUserIds: string[];
};

export function defaultBrowserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export function todayLocalDateInput(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function createEmptyOnCallScheduleFormValues(): OnCallScheduleFormValues {
  return {
    name: "",
    rotationLengthDays: 7,
    timezone: defaultBrowserTimezone(),
    handoffLocalTime: "09:00",
    handoffDayOfWeek: "1",
    effectiveFrom: todayLocalDateInput(),
    scopeMode: "ORGANIZATION",
    teamId: "",
    serviceId: "",
    participantUserIds: [],
  };
}

export function detailToFormValues(detail: OnCallScheduleDetail): OnCallScheduleFormValues {
  const schedule = detail.schedule;
  const scope = onCallScopeKind(schedule.teamId, schedule.serviceId);
  const scopeMode: OnCallScopeMode =
    scope === "TEAM" ? "TEAM" : scope === "SERVICE" ? "SERVICE" : "ORGANIZATION";

  const sorted = [...detail.participants].sort((a, b) => a.position - b.position);

  return {
    name: schedule.name,
    rotationLengthDays: schedule.rotationLengthDays === 1 ? 1 : 7,
    timezone: schedule.timezone,
    handoffLocalTime: formatOnCallHandoffLocalTime(schedule.handoffLocalTime),
    handoffDayOfWeek:
      schedule.rotationLengthDays === 7 && schedule.handoffDayOfWeek != null
        ? String(schedule.handoffDayOfWeek)
        : "",
    effectiveFrom: schedule.effectiveFrom,
    scopeMode,
    teamId: schedule.teamId ?? "",
    serviceId: schedule.serviceId ?? "",
    participantUserIds: sorted.map((p) => p.userId),
  };
}

export function handoffLocalTimeToApi(handoffLocalTime: string): string {
  const trimmed = handoffLocalTime.trim();
  if (/^\d{2}:\d{2}$/.test(trimmed)) {
    return `${trimmed}:00`;
  }
  return trimmed;
}

export function applyScopeModeChange(
  values: OnCallScheduleFormValues,
  nextMode: OnCallScopeMode,
): OnCallScheduleFormValues {
  const clearParticipants = nextMode !== values.scopeMode;
  if (nextMode === "ORGANIZATION") {
    return {
      ...values,
      scopeMode: nextMode,
      teamId: "",
      serviceId: "",
      participantUserIds: clearParticipants ? [] : values.participantUserIds,
    };
  }
  if (nextMode === "TEAM") {
    return {
      ...values,
      scopeMode: nextMode,
      serviceId: "",
      participantUserIds: clearParticipants ? [] : values.participantUserIds,
    };
  }
  return {
    ...values,
    scopeMode: nextMode,
    teamId: "",
    participantUserIds: clearParticipants ? [] : values.participantUserIds,
  };
}

export function addParticipant(
  participantUserIds: string[],
  userId: string,
): string[] | { error: string } {
  if (!userId) {
    return { error: "Select a participant to add." };
  }
  if (participantUserIds.includes(userId)) {
    return { error: "That user is already in the rotation." };
  }
  return [...participantUserIds, userId];
}

export function moveParticipant(
  participantUserIds: string[],
  index: number,
  direction: "up" | "down",
): string[] {
  const next = [...participantUserIds];
  const target = direction === "up" ? index - 1 : index + 1;
  if (target < 0 || target >= next.length) {
    return participantUserIds;
  }
  const [item] = next.splice(index, 1);
  next.splice(target, 0, item);
  return next;
}

export function removeParticipant(participantUserIds: string[], index: number): string[] {
  return participantUserIds.filter((_, i) => i !== index);
}

export type OnCallScheduleFormFieldErrors = Partial<
  Record<
    | "name"
    | "timezone"
    | "handoffLocalTime"
    | "handoffDayOfWeek"
    | "effectiveFrom"
    | "teamId"
    | "serviceId"
    | "participants"
    | "form",
    string
  >
>;

export function validateOnCallScheduleForm(
  values: OnCallScheduleFormValues,
): OnCallScheduleFormFieldErrors {
  const errors: OnCallScheduleFormFieldErrors = {};

  if (!values.name.trim()) {
    errors.name = "Name is required.";
  } else if (values.name.trim().length > 100) {
    errors.name = "Name must be at most 100 characters.";
  }

  if (!values.timezone.trim()) {
    errors.timezone = "Timezone is required.";
  } else if (values.timezone.trim().length > 64) {
    errors.timezone = "Timezone must be at most 64 characters.";
  }

  if (!values.handoffLocalTime.trim()) {
    errors.handoffLocalTime = "Handoff time is required.";
  }

  if (values.rotationLengthDays === 7) {
    const dow = Number(values.handoffDayOfWeek);
    if (!values.handoffDayOfWeek || dow < 1 || dow > 7) {
      errors.handoffDayOfWeek = "Select the weekly handoff weekday.";
    }
  }

  if (!values.effectiveFrom.trim()) {
    errors.effectiveFrom = "Effective-from date is required.";
  }

  if (values.scopeMode === "TEAM" && !values.teamId) {
    errors.teamId = "Select a team for team-scoped schedules.";
  }
  if (values.scopeMode === "SERVICE" && !values.serviceId) {
    errors.serviceId = "Select a service for service-scoped schedules.";
  }

  if (values.participantUserIds.length === 0) {
    errors.participants = "Add at least one rotation participant.";
  }

  const unique = new Set(values.participantUserIds);
  if (unique.size !== values.participantUserIds.length) {
    errors.participants = "Each participant can appear only once.";
  }

  return errors;
}

export function buildCreateOnCallScheduleInput(
  values: OnCallScheduleFormValues,
): CreateOnCallScheduleInput {
  const rotationLengthDays = values.rotationLengthDays;
  const handoffDayOfWeek =
    rotationLengthDays === 7 ? Number(values.handoffDayOfWeek) : null;

  const input: CreateOnCallScheduleInput = {
    name: values.name.trim(),
    timezone: values.timezone.trim(),
    rotationLengthDays,
    handoffLocalTime: handoffLocalTimeToApi(values.handoffLocalTime),
    effectiveFrom: values.effectiveFrom,
    participantUserIds: values.participantUserIds,
  };

  if (rotationLengthDays === 7) {
    input.handoffDayOfWeek = handoffDayOfWeek;
  } else {
    input.handoffDayOfWeek = null;
  }

  if (values.scopeMode === "TEAM" && values.teamId) {
    input.teamId = values.teamId;
  } else if (values.scopeMode === "SERVICE" && values.serviceId) {
    input.serviceId = values.serviceId;
  } else {
    input.teamId = null;
    input.serviceId = null;
  }

  return input;
}

function orderedIdsEqual(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id, index) => id === b[index]);
}

export function buildUpdateOnCallScheduleBody(
  initial: OnCallScheduleFormValues,
  current: OnCallScheduleFormValues,
): UpdateOnCallScheduleBody {
  const body: UpdateOnCallScheduleBody = {};

  if (current.name.trim() !== initial.name.trim()) {
    body.name = current.name.trim();
  }

  if (current.timezone.trim() !== initial.timezone.trim()) {
    body.timezone = current.timezone.trim();
  }

  if (current.rotationLengthDays !== initial.rotationLengthDays) {
    body.rotationLengthDays = current.rotationLengthDays;
  }

  const apiHandoff = handoffLocalTimeToApi(current.handoffLocalTime);
  const initialApiHandoff = handoffLocalTimeToApi(initial.handoffLocalTime);
  if (apiHandoff !== initialApiHandoff) {
    body.handoffLocalTime = apiHandoff;
  }

  const currentDow =
    current.rotationLengthDays === 7 ? Number(current.handoffDayOfWeek) : null;
  const initialDow =
    initial.rotationLengthDays === 7 && initial.handoffDayOfWeek
      ? Number(initial.handoffDayOfWeek)
      : null;

  if (current.rotationLengthDays === 7) {
    if (currentDow !== initialDow || current.rotationLengthDays !== initial.rotationLengthDays) {
      body.handoffDayOfWeek = currentDow;
    }
  } else if (initial.rotationLengthDays === 7 && current.rotationLengthDays === 1) {
    body.handoffDayOfWeek = null;
  }

  if (current.effectiveFrom !== initial.effectiveFrom) {
    body.effectiveFrom = current.effectiveFrom;
  }

  const scopeChanged =
    current.scopeMode !== initial.scopeMode ||
    (current.scopeMode === "TEAM" && current.teamId !== initial.teamId) ||
    (current.scopeMode === "SERVICE" && current.serviceId !== initial.serviceId);

  if (scopeChanged) {
    if (current.scopeMode === "ORGANIZATION") {
      body.clearScope = true;
    } else if (current.scopeMode === "TEAM") {
      body.teamId = current.teamId || null;
    } else {
      body.serviceId = current.serviceId || null;
    }
  }

  if (!orderedIdsEqual(current.participantUserIds, initial.participantUserIds)) {
    body.participantUserIds = current.participantUserIds;
  }

  return body;
}

/** Whether management actions should be offered in the UI. */
export function showOnCallManagementActions(canManage: boolean): boolean {
  return canManage;
}
