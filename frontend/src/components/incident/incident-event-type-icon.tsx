import type { IncidentEventType } from "@/types/incident";
import {
  Activity,
  FileText,
  Flag,
  Layers,
  Pencil,
  PlusCircle,
  UserRound,
} from "lucide-react";

export function IncidentEventTypeIcon({
  type,
  className,
}: {
  type: IncidentEventType;
  className?: string;
}) {
  switch (type) {
    case "INCIDENT_CREATED":
      return <PlusCircle className={className} aria-hidden="true" />;
    case "STATUS_CHANGED":
      return <Activity className={className} aria-hidden="true" />;
    case "SEVERITY_CHANGED":
      return <Flag className={className} aria-hidden="true" />;
    case "SERVICE_CHANGED":
      return <Layers className={className} aria-hidden="true" />;
    case "COMMANDER_CHANGED":
      return <UserRound className={className} aria-hidden="true" />;
    case "TITLE_CHANGED":
    case "DESCRIPTION_CHANGED":
      return <Pencil className={className} aria-hidden="true" />;
    default:
      return <FileText className={className} aria-hidden="true" />;
  }
}
