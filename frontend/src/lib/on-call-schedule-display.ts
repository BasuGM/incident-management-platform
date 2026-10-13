import type { OnCallScheduleParticipant, OnCallScheduleSummary } from "@/types/on-call-schedule";

export type OnCallScopeKind = "ORGANIZATION" | "TEAM" | "SERVICE";

const ISO_DAY_LABELS = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
] as const;

export function onCallScopeKind(
  teamId: string | null | undefined,
  serviceId: string | null | undefined,
): OnCallScopeKind {
  if (teamId) {
    return "TEAM";
  }
  if (serviceId) {
    return "SERVICE";
  }
  return "ORGANIZATION";
}

export type OnCallScopeNameLookup = {
  teamNames?: ReadonlyMap<string, string>;
  serviceNames?: ReadonlyMap<string, string>;
};

export function formatOnCallScopeLabel(
  summary: Pick<OnCallScheduleSummary, "teamId" | "serviceId">,
  lookup: OnCallScopeNameLookup = {},
): string {
  const kind = onCallScopeKind(summary.teamId, summary.serviceId);
  if (kind === "TEAM") {
    const name = summary.teamId ? lookup.teamNames?.get(summary.teamId) : undefined;
    return name ? `Team · ${name}` : "Team-scoped";
  }
  if (kind === "SERVICE") {
    const name = summary.serviceId ? lookup.serviceNames?.get(summary.serviceId) : undefined;
    return name ? `Service · ${name}` : "Service-scoped";
  }
  return "Organization-wide";
}

export function formatOnCallHandoffLocalTime(handoffLocalTime: string): string {
  const trimmed = handoffLocalTime.trim();
  if (/^\d{2}:\d{2}:\d{2}$/.test(trimmed)) {
    return trimmed.slice(0, 5);
  }
  return trimmed;
}

export function formatIsoDayOfWeek(day: number | null | undefined): string | null {
  if (day == null) {
    return null;
  }
  if (day < 1 || day > 7) {
    return `Day ${day}`;
  }
  return ISO_DAY_LABELS[day - 1];
}

export function formatOnCallCadence(summary: Pick<
  OnCallScheduleSummary,
  "rotationLengthDays" | "handoffLocalTime" | "handoffDayOfWeek"
>): string {
  const time = formatOnCallHandoffLocalTime(summary.handoffLocalTime);
  if (summary.rotationLengthDays === 7) {
    const day = formatIsoDayOfWeek(summary.handoffDayOfWeek) ?? "configured day";
    return `Weekly · ${day} at ${time}`;
  }
  if (summary.rotationLengthDays === 1) {
    return `Daily at ${time}`;
  }
  return `Every ${summary.rotationLengthDays} days at ${time}`;
}

export function formatOnCallEffectiveFrom(effectiveFrom: string): string {
  try {
    const [year, month, day] = effectiveFrom.split("-").map((part) => Number(part));
    if (year && month && day) {
      return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeZone: "UTC",
      }).format(new Date(Date.UTC(year, month - 1, day)));
    }
  } catch {
    // fall through
  }
  return effectiveFrom;
}

export function formatInstantInTimeZone(iso: string, timeZone: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      timeZone,
      dateStyle: "medium",
      timeStyle: "short",
      timeZoneName: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function sortOnCallParticipantsByPosition(
  participants: OnCallScheduleParticipant[],
): OnCallScheduleParticipant[] {
  return [...participants].sort((a, b) => a.position - b.position);
}

export function buildOnCallScopeNameMaps(
  teams: { id: string; name: string }[] | undefined,
  services: { id: string; name: string }[] | undefined,
): OnCallScopeNameLookup {
  return {
    teamNames: teams ? new Map(teams.map((team) => [team.id, team.name])) : undefined,
    serviceNames: services ? new Map(services.map((service) => [service.id, service.name])) : undefined,
  };
}

export function scheduleNeedsScopeNameLookup(
  schedules: Pick<OnCallScheduleSummary, "teamId" | "serviceId">[],
): boolean {
  return schedules.some((schedule) => schedule.teamId || schedule.serviceId);
}
