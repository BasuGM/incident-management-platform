import { IncidentEventTypeIcon } from "@/components/incident/incident-event-type-icon";
import {
  eventDetailLines,
  eventPrimaryText,
  formatEventAbsoluteTime,
  formatEventRelativeTime,
  type EventNameContext,
} from "@/lib/incident-event-display";
import type { IncidentEvent } from "@/types/incident";

type IncidentTimelineItemProps = {
  event: IncidentEvent;
  names: EventNameContext;
};

export function IncidentTimelineItem({ event, names }: IncidentTimelineItemProps) {
  const primary = eventPrimaryText(event, names);
  const details = eventDetailLines(event, names);

  return (
    <li className="relative flex gap-3 pb-6 last:pb-0">
      <div
        className="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border bg-background"
        aria-hidden="true"
      >
        <IncidentEventTypeIcon type={event.type} className="h-4 w-4 text-muted-foreground" />
      </div>
      <div className="min-w-0 flex-1 space-y-1">
        <p className="text-sm font-medium leading-snug">{primary}</p>
        {details.length > 0 && (
          <ul className="space-y-0.5 text-sm text-muted-foreground">
            {details.map((line, index) => (
              <li key={`${event.id}-detail-${index}`} className="break-words">
                {line}
              </li>
            ))}
          </ul>
        )}
        <p className="text-xs text-muted-foreground">
          <time dateTime={event.createdAt} title={formatEventAbsoluteTime(event.createdAt)}>
            {formatEventRelativeTime(event.createdAt)}
          </time>
          <span className="mx-1" aria-hidden="true">·</span>
          <span>{formatEventAbsoluteTime(event.createdAt)}</span>
        </p>
      </div>
    </li>
  );
}
