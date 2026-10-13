export type OnCallScheduleStatus = "ACTIVE" | "ARCHIVED";

export type OnCallScheduleListStatus = "ACTIVE" | "ARCHIVED" | "ALL";

export type OnCallScheduleSummary = {
  id: string;
  name: string;
  status: OnCallScheduleStatus;
  timezone: string;
  rotationLengthDays: number;
  handoffLocalTime: string;
  handoffDayOfWeek: number | null;
  effectiveFrom: string;
  teamId: string | null;
  serviceId: string | null;
  participantCount: number;
  createdAt: string;
  updatedAt: string;
};

export type OnCallScheduleParticipant = {
  userId: string;
  displayName: string;
  position: number;
};

/** Detail response: nested `schedule` + ordered `participants` (backend contract). */
export type OnCallScheduleDetail = {
  schedule: OnCallScheduleSummary;
  participants: OnCallScheduleParticipant[];
};

export type OnCallCoverageShift = {
  userId: string;
  displayName: string;
  shiftStartsAt: string;
  shiftEndsAt: string;
};

export type OnCallCoverage = {
  scheduleId: string;
  status: OnCallScheduleStatus;
  timezone: string;
  queriedAt: string;
  current: OnCallCoverageShift | null;
  nextHandoffAt: string;
  upcoming: OnCallCoverageShift[];
};

export type CreateOnCallScheduleInput = {
  name: string;
  timezone: string;
  rotationLengthDays: number;
  handoffLocalTime: string;
  handoffDayOfWeek?: number | null;
  effectiveFrom: string;
  teamId?: string | null;
  serviceId?: string | null;
  participantUserIds: string[];
};

export type UpdateOnCallScheduleBody = {
  name?: string;
  timezone?: string;
  rotationLengthDays?: number;
  handoffLocalTime?: string;
  handoffDayOfWeek?: number | null;
  effectiveFrom?: string;
  teamId?: string | null;
  serviceId?: string | null;
  clearScope?: boolean;
  participantUserIds?: string[];
};

export type ListOnCallSchedulesParams = {
  /** Omit for backend default (`ACTIVE`). */
  status?: OnCallScheduleListStatus;
};

export type GetOnCallCoverageParams = {
  /** ISO-8601 instant; omit for server current time. */
  at?: string;
  upcoming?: number;
};
