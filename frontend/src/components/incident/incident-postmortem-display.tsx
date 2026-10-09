"use client";

import { formatIncidentTimestamp, memberDisplayName } from "@/lib/incident-display";
import type { IncidentPostmortem } from "@/types/incident";

const SECTIONS: { key: keyof Pick<
  IncidentPostmortem,
  "summary" | "impact" | "rootCause" | "resolution" | "lessonsLearned" | "correctiveActions"
>; label: string }[] = [
  { key: "summary", label: "Summary" },
  { key: "impact", label: "Impact" },
  { key: "rootCause", label: "Root cause" },
  { key: "resolution", label: "Resolution" },
  { key: "lessonsLearned", label: "Lessons learned" },
  { key: "correctiveActions", label: "Corrective actions" },
];

type IncidentPostmortemDisplayProps = {
  postmortem: IncidentPostmortem;
};

export function IncidentPostmortemDisplay({ postmortem }: IncidentPostmortemDisplayProps) {
  const authorName = memberDisplayName(
    postmortem.authorFirstName,
    postmortem.authorLastName,
    postmortem.authorEmail,
  );

  const publisherName =
    postmortem.publishedById && postmortem.publishedByEmail
      ? memberDisplayName(
          postmortem.publishedByFirstName ?? "",
          postmortem.publishedByLastName ?? "",
          postmortem.publishedByEmail,
        )
      : null;

  return (
    <div className="space-y-4">
      <div className="space-y-1 text-sm text-muted-foreground">
        <p>
          <span className="font-medium text-foreground">Author:</span> {authorName}
        </p>
        <p>
          <span className="font-medium text-foreground">Created:</span>{" "}
          {formatIncidentTimestamp(postmortem.createdAt)}
        </p>
        <p>
          <span className="font-medium text-foreground">Updated:</span>{" "}
          {formatIncidentTimestamp(postmortem.updatedAt)}
        </p>
        {postmortem.publishedAt && (
          <p>
            <span className="font-medium text-foreground">Published:</span>{" "}
            {formatIncidentTimestamp(postmortem.publishedAt)}
            {publisherName ? ` by ${publisherName}` : ""}
          </p>
        )}
        {postmortem.archivedAt && (
          <p>
            <span className="font-medium text-foreground">Archived:</span>{" "}
            {formatIncidentTimestamp(postmortem.archivedAt)}
          </p>
        )}
      </div>

      <div>
        <p className="text-sm font-medium text-muted-foreground">Title</p>
        <p className="mt-1 text-sm font-medium">{postmortem.title}</p>
      </div>

      {SECTIONS.map(({ key, label }) => (
        <div key={key}>
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-sm">
            {postmortem[key]?.trim() ? postmortem[key] : "—"}
          </p>
        </div>
      ))}
    </div>
  );
}
