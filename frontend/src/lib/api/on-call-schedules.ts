import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type {
  CreateOnCallScheduleInput,
  GetOnCallCoverageParams,
  ListOnCallSchedulesParams,
  OnCallCoverage,
  OnCallScheduleDetail,
  OnCallScheduleSummary,
  UpdateOnCallScheduleBody,
} from "@/types/on-call-schedule";

export const DEFAULT_ON_CALL_UPCOMING = 3;
export const MAX_ON_CALL_UPCOMING = 20;

export function onCallSchedulesBasePath(organizationId: string): string {
  return `/api/v1/organizations/${organizationId}/on-call-schedules`;
}

export function onCallSchedulePath(organizationId: string, scheduleId: string): string {
  return `${onCallSchedulesBasePath(organizationId)}/${scheduleId}`;
}

export function listOnCallSchedulesPath(
  organizationId: string,
  params: ListOnCallSchedulesParams = {},
): string {
  const base = onCallSchedulesBasePath(organizationId);
  if (params.status === undefined) {
    return base;
  }
  const search = new URLSearchParams({ status: params.status });
  return `${base}?${search.toString()}`;
}

export function onCallCoveragePath(
  organizationId: string,
  scheduleId: string,
  params: GetOnCallCoverageParams = {},
): string {
  const search = new URLSearchParams();
  if (params.at !== undefined && params.at !== "") {
    search.set("at", params.at);
  }
  if (params.upcoming !== undefined) {
    search.set("upcoming", String(params.upcoming));
  }
  const query = search.toString();
  const path = `${onCallSchedulePath(organizationId, scheduleId)}/coverage`;
  return query ? `${path}?${query}` : path;
}

/** PATCH body: only fields present on `body` are sent. */
export function serializeUpdateOnCallScheduleBody(
  body: UpdateOnCallScheduleBody,
): Record<string, unknown> {
  const payload: Record<string, unknown> = {};
  const keys = [
    "name",
    "timezone",
    "rotationLengthDays",
    "handoffLocalTime",
    "handoffDayOfWeek",
    "effectiveFrom",
    "teamId",
    "serviceId",
    "clearScope",
    "participantUserIds",
  ] as const;
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(body, key)) {
      payload[key] = body[key];
    }
  }
  return payload;
}

export async function listOnCallSchedules(
  organizationId: string,
  params: ListOnCallSchedulesParams = {},
): Promise<OnCallScheduleSummary[]> {
  return getAuthenticatedClient().get<OnCallScheduleSummary[]>(
    listOnCallSchedulesPath(organizationId, params),
  );
}

export async function getOnCallSchedule(
  organizationId: string,
  scheduleId: string,
): Promise<OnCallScheduleDetail> {
  return getAuthenticatedClient().get<OnCallScheduleDetail>(
    onCallSchedulePath(organizationId, scheduleId),
  );
}

export async function createOnCallSchedule(
  organizationId: string,
  input: CreateOnCallScheduleInput,
): Promise<OnCallScheduleDetail> {
  return getAuthenticatedClient().post<OnCallScheduleDetail>(
    onCallSchedulesBasePath(organizationId),
    input,
  );
}

export async function updateOnCallSchedule(
  organizationId: string,
  scheduleId: string,
  body: UpdateOnCallScheduleBody,
): Promise<OnCallScheduleDetail> {
  return getAuthenticatedClient().patch<OnCallScheduleDetail>(
    onCallSchedulePath(organizationId, scheduleId),
    serializeUpdateOnCallScheduleBody(body),
  );
}

export async function archiveOnCallSchedule(
  organizationId: string,
  scheduleId: string,
): Promise<OnCallScheduleDetail> {
  return getAuthenticatedClient().post<OnCallScheduleDetail>(
    `${onCallSchedulePath(organizationId, scheduleId)}/archive`,
  );
}

export async function reactivateOnCallSchedule(
  organizationId: string,
  scheduleId: string,
): Promise<OnCallScheduleDetail> {
  return getAuthenticatedClient().post<OnCallScheduleDetail>(
    `${onCallSchedulePath(organizationId, scheduleId)}/reactivate`,
  );
}

export async function getOnCallCoverage(
  organizationId: string,
  scheduleId: string,
  params: GetOnCallCoverageParams = {},
): Promise<OnCallCoverage> {
  const upcoming =
    params.upcoming !== undefined
      ? Math.min(Math.max(params.upcoming, 0), MAX_ON_CALL_UPCOMING)
      : undefined;
  return getAuthenticatedClient().get<OnCallCoverage>(
    onCallCoveragePath(organizationId, scheduleId, { ...params, upcoming }),
  );
}
