"use client";

import { IncidentTimelineItem } from "@/components/incident/incident-timeline-item";
import { Button } from "@/components/ui/button";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getIncidentEvents } from "@/lib/api/incidents";
import {
  defaultMemberName,
  defaultServiceName,
  type EventNameContext,
} from "@/lib/incident-event-display";
import { memberDisplayName } from "@/lib/incident-display";
import { listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { useAuth } from "@/components/providers/auth-provider";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";

const TIMELINE_PAGE_SIZE = 20;

type IncidentTimelineProps = {
  organizationId: string;
  incidentId: string;
};

export function IncidentTimeline({ organizationId, incidentId }: IncidentTimelineProps) {
  const { user } = useAuth();

  const eventsQuery = useInfiniteQuery({
    queryKey: ["incident-events", organizationId, incidentId, TIMELINE_PAGE_SIZE, user?.id],
    queryFn: ({ pageParam }) =>
      getIncidentEvents(organizationId, incidentId, pageParam, TIMELINE_PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    enabled: Boolean(organizationId && incidentId && user?.id),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
    enabled: Boolean(organizationId && user?.id),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
    enabled: Boolean(organizationId && user?.id),
  });

  const names: EventNameContext = useMemo(() => {
    const services = servicesQuery.data ?? [];
    const members = membersQuery.data ?? [];
    return {
      serviceName: (id) => {
        if (!id) {
          return "Unassigned";
        }
        const service = services.find((item) => item.id === id);
        return service?.name ?? defaultServiceName(id);
      },
      memberName: (id) => {
        if (!id) {
          return "Unassigned";
        }
        const member = members.find((item) => item.userId === id);
        if (!member) {
          return defaultMemberName(id);
        }
        return memberDisplayName(member.firstName, member.lastName, member.email);
      },
    };
  }, [membersQuery.data, servicesQuery.data]);

  const events = eventsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const hasMore = eventsQuery.hasNextPage;

  return (
    <section
      className="rounded-lg border p-4"
      aria-labelledby="incident-timeline-heading"
    >
      <h2 id="incident-timeline-heading" className="text-lg font-semibold tracking-tight">
        Timeline
      </h2>

      {eventsQuery.isPending && (
        <p className="mt-4 text-sm text-muted-foreground" role="status">
          Loading timeline…
        </p>
      )}

      {eventsQuery.isError && (
        <div className="mt-4 space-y-2" role="alert">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(eventsQuery.error, "Unable to load timeline.")}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => eventsQuery.refetch()}
          >
            Retry
          </Button>
        </div>
      )}

      {eventsQuery.isSuccess && events.length === 0 && (
        <p className="mt-4 text-sm text-muted-foreground">No timeline events yet.</p>
      )}

      {events.length > 0 && (
        <ol className="relative mt-4 space-y-0 border-l border-border pl-4 ml-4" aria-live="polite">
          {events.map((event) => (
            <IncidentTimelineItem key={event.id} event={event} names={names} />
          ))}
        </ol>
      )}

      {hasMore && (
        <div className="mt-4">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={eventsQuery.isFetchingNextPage}
            onClick={() => eventsQuery.fetchNextPage()}
            aria-label="Load more timeline events"
          >
            {eventsQuery.isFetchingNextPage ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </section>
  );
}
