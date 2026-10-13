import type { OnCallScheduleStatus } from "@/types/on-call-schedule";

const statusClass: Record<OnCallScheduleStatus, string> = {
  ACTIVE: "border-emerald-600/40 bg-emerald-600/10 text-emerald-900 dark:text-emerald-100",
  ARCHIVED: "border-muted-foreground/40 bg-muted text-muted-foreground",
};

const statusLabel: Record<OnCallScheduleStatus, string> = {
  ACTIVE: "Active",
  ARCHIVED: "Archived",
};

export function OnCallScheduleStatusBadge({ status }: { status: OnCallScheduleStatus }) {
  return (
    <span
      className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-medium ${statusClass[status]}`}
    >
      {statusLabel[status]}
    </span>
  );
}
