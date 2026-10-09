"use client";

import { IncidentPostmortemStatusBadge } from "@/components/incident/incident-postmortem-status-badge";
import {
  incidentDetailPath,
  postmortemAuthorDisplayName,
  postmortemListTimestamp,
  postmortemPublisherDisplayName,
  summarizePostmortemText,
} from "@/lib/organization-postmortems-library";
import { formatIncidentTimestamp } from "@/lib/incident-display";
import type { IncidentPostmortem } from "@/types/incident";
import Link from "next/link";

type OrganizationPostmortemListItemProps = {
  organizationId: string;
  postmortem: IncidentPostmortem;
  showStatus: boolean;
};

export function OrganizationPostmortemListItem({
  organizationId,
  postmortem,
  showStatus,
}: OrganizationPostmortemListItemProps) {
  const summaryPreview = summarizePostmortemText(postmortem.summary);
  const publisher = postmortemPublisherDisplayName(postmortem);
  const timestamp = postmortemListTimestamp(postmortem);

  return (
    <article className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 space-y-1">
          <h3 className="text-base font-semibold leading-snug">
            <Link
              href={incidentDetailPath(organizationId, postmortem.incidentId)}
              className="text-primary hover:underline"
            >
              {postmortem.title.trim() || "Untitled postmortem"}
            </Link>
          </h3>
          <p className="text-xs text-muted-foreground">
            Incident ID: <span className="font-mono">{postmortem.incidentId}</span>
          </p>
        </div>
        {showStatus && <IncidentPostmortemStatusBadge status={postmortem.status} />}
      </div>

      {summaryPreview && (
        <p className="mt-3 whitespace-pre-wrap break-words text-sm text-muted-foreground">
          {summaryPreview}
        </p>
      )}

      <dl className="mt-3 grid gap-1 text-xs text-muted-foreground sm:grid-cols-2">
        <div>
          <dt className="font-medium text-foreground">Author</dt>
          <dd>{postmortemAuthorDisplayName(postmortem)}</dd>
        </div>
        {publisher && (
          <div>
            <dt className="font-medium text-foreground">Published by</dt>
            <dd>{publisher}</dd>
          </div>
        )}
        {timestamp && (
          <div>
            <dt className="font-medium text-foreground">
              {postmortem.publishedAt ? "Published" : "Updated"}
            </dt>
            <dd>{formatIncidentTimestamp(timestamp)}</dd>
          </div>
        )}
      </dl>

      <div className="mt-3">
        <Link
          href={incidentDetailPath(organizationId, postmortem.incidentId)}
          className="text-sm text-primary hover:underline"
        >
          Open incident postmortem
        </Link>
      </div>
    </article>
  );
}
