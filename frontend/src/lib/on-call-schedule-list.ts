import type { OnCallScheduleListStatus } from "@/types/on-call-schedule";

export type OnCallListStatusFilter = "ACTIVE" | "ARCHIVED" | "ALL";

export const ON_CALL_LIST_STATUS_FILTER_OPTIONS: {
  value: OnCallListStatusFilter;
  label: string;
}[] = [
  { value: "ACTIVE", label: "Active" },
  { value: "ARCHIVED", label: "Archived" },
  { value: "ALL", label: "All" },
];

/** Maps UI filter to list API params (ACTIVE uses backend default — omit `status`). */
export function onCallListFilterToApiStatus(
  filter: OnCallListStatusFilter,
): OnCallScheduleListStatus | undefined {
  if (filter === "ACTIVE") {
    return undefined;
  }
  return filter;
}

export function onCallListFilterToQueryKeyStatus(filter: OnCallListStatusFilter): OnCallScheduleListStatus | "DEFAULT" {
  if (filter === "ACTIVE") {
    return "DEFAULT";
  }
  return filter;
}

export function onCallSchedulesListPath(organizationId: string): string {
  return `/organizations/${organizationId}/on-call`;
}

export function onCallScheduleDetailPath(organizationId: string, scheduleId: string): string {
  return `/organizations/${organizationId}/on-call/${scheduleId}`;
}

export function onCallScheduleCreatePath(organizationId: string): string {
  return `/organizations/${organizationId}/on-call/new`;
}

export function onCallScheduleEditPath(organizationId: string, scheduleId: string): string {
  return `/organizations/${organizationId}/on-call/${scheduleId}/edit`;
}

export function onCallListEmptyMessage(
  filter: OnCallListStatusFilter,
  canManage: boolean,
): { title: string; description: string } {
  if (filter === "ARCHIVED") {
    return {
      title: "No archived schedules",
      description: "Archived rotations are hidden from the default list. Active schedules appear when the Active filter is selected.",
    };
  }
  if (filter === "ALL") {
    return {
      title: "No on-call schedules",
      description: canManage
        ? "Create a schedule to define who is on call for this organization."
        : "This organization does not have any on-call schedules yet.",
    };
  }
  return {
    title: "No active on-call schedules",
    description: canManage
      ? "Create a schedule to define who is on call. Archived schedules can be viewed with the Archived or All filter."
      : "There are no active on-call rotations for this organization. Ask an administrator if you expected a schedule here.",
  };
}
