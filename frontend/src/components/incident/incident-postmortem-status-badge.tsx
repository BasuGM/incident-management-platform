import type { IncidentPostmortemStatus } from "@/types/incident";

const statusClass: Record<IncidentPostmortemStatus, string> = {
  DRAFT: "border-amber-500/50 bg-amber-500/10 text-amber-900 dark:text-amber-100",
  PUBLISHED: "border-emerald-600/40 bg-emerald-600/10 text-emerald-900 dark:text-emerald-100",
  ARCHIVED: "border-muted-foreground/40 bg-muted text-muted-foreground",
};

const statusLabel: Record<IncidentPostmortemStatus, string> = {
  DRAFT: "Draft",
  PUBLISHED: "Published",
  ARCHIVED: "Archived",
};

export function IncidentPostmortemStatusBadge({ status }: { status: IncidentPostmortemStatus }) {
  return (
    <span
      className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-medium ${statusClass[status]}`}
    >
      {statusLabel[status]}
    </span>
  );
}
