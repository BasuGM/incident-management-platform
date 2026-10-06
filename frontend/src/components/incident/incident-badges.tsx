import type { IncidentSeverity, IncidentStatus } from "@/types/incident";

const severityClass: Record<IncidentSeverity, string> = {
  SEV1: "border-destructive/60 bg-destructive/10 text-destructive",
  SEV2: "border-orange-500/50 bg-orange-500/10 text-orange-800 dark:text-orange-200",
  SEV3: "border-amber-500/50 bg-amber-500/10 text-amber-900 dark:text-amber-100",
  SEV4: "border-muted-foreground/30 bg-muted text-muted-foreground",
};

const statusClass: Record<IncidentStatus, string> = {
  OPEN: "border-blue-500/40 bg-blue-500/10 text-blue-900 dark:text-blue-100",
  ACKNOWLEDGED: "border-violet-500/40 bg-violet-500/10 text-violet-900 dark:text-violet-100",
  RESOLVED: "border-emerald-600/40 bg-emerald-600/10 text-emerald-900 dark:text-emerald-100",
  CANCELLED: "border-muted-foreground/40 bg-muted text-muted-foreground",
};

export function IncidentSeverityBadge({ severity }: { severity: IncidentSeverity }) {
  return (
    <span
      className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-medium ${severityClass[severity]}`}
    >
      {severity}
    </span>
  );
}

export function IncidentStatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span
      className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-medium ${statusClass[status]}`}
    >
      {status}
    </span>
  );
}
