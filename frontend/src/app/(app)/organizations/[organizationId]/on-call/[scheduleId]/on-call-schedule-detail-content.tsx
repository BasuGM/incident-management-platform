"use client";

import { OnCallScheduleStatusBadge } from "@/components/on-call/on-call-schedule-status-badge";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button, buttonVariants } from "@/components/ui/button";
import { useAuth } from "@/components/providers/auth-provider";
import { ApiError } from "@/lib/api/client";
import { getApiErrorBody, getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { listTeams } from "@/lib/api/teams";
import {
  useArchiveOnCallScheduleMutation,
  useOnCallCoverageQuery,
  useOnCallScheduleDetailQuery,
  useReactivateOnCallScheduleMutation,
} from "@/lib/hooks/use-organization-on-call-schedules";
import {
  buildOnCallScopeNameMaps,
  formatInstantInTimeZone,
  formatOnCallCadence,
  formatOnCallEffectiveFrom,
  formatOnCallHandoffLocalTime,
  formatOnCallScopeLabel,
  formatIsoDayOfWeek,
  sortOnCallParticipantsByPosition,
} from "@/lib/on-call-schedule-display";
import { onCallScheduleEditPath, onCallSchedulesListPath } from "@/lib/on-call-schedule-list";
import { canManageOrganization } from "@/lib/organization-rbac";
import type { OnCallCoverageShift } from "@/types/on-call-schedule";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

function coverageUnavailableMessage(error: unknown, scheduleStatus: string | undefined): string {
  const body = getApiErrorBody(error);
  if (body?.error === "SCHEDULE_NOT_ACTIVE" || scheduleStatus === "ARCHIVED") {
    return "Coverage is not available for archived schedules. Reactivate the schedule to view on-call coverage again.";
  }
  if (body?.error === "SCHEDULE_EMPTY") {
    return "This schedule has no participants, so coverage cannot be calculated.";
  }
  if (error instanceof ApiError && error.status === 404) {
    return "Schedule not found or you do not have access.";
  }
  return getApiErrorMessage(error, "Failed to load coverage");
}

function ShiftRow({
  shift,
  timeZone,
  label,
}: {
  shift: OnCallCoverageShift;
  timeZone: string;
  label?: string;
}) {
  return (
    <li className="rounded-md border px-3 py-2">
      {label ? <p className="text-xs font-medium text-muted-foreground">{label}</p> : null}
      <p className="font-medium">{shift.displayName}</p>
      <p className="text-sm text-muted-foreground">
        {formatInstantInTimeZone(shift.shiftStartsAt, timeZone)}
        {" → "}
        {formatInstantInTimeZone(shift.shiftEndsAt, timeZone)}
      </p>
    </li>
  );
}

export function OnCallScheduleDetailContent({
  organizationId,
  scheduleId,
}: {
  organizationId: string;
  scheduleId: string;
}) {
  const { user } = useAuth();
  const [lifecycleFeedback, setLifecycleFeedback] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const detailQuery = useOnCallScheduleDetailQuery({
    organizationId,
    scheduleId,
    enabled: Boolean(organizationId && scheduleId && user?.id),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;
  const detail = detailQuery.data;
  const schedule = detail?.schedule;

  const coverageQuery = useOnCallCoverageQuery({
    organizationId,
    scheduleId,
    enabled: Boolean(organizationId && scheduleId && user?.id && schedule?.status === "ACTIVE"),
  });

  const archiveMutation = useArchiveOnCallScheduleMutation(organizationId, scheduleId);
  const reactivateMutation = useReactivateOnCallScheduleMutation(organizationId, scheduleId);

  const needsScopeLookup = Boolean(schedule?.teamId || schedule?.serviceId);

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
    enabled: Boolean(organization && needsScopeLookup),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
    enabled: Boolean(organization && needsScopeLookup),
  });

  const scopeLookup = buildOnCallScopeNameMaps(teamsQuery.data, servicesQuery.data);
  const participants = sortOnCallParticipantsByPosition(detail?.participants ?? []);
  const coverage = coverageQuery.data;
  const timeZone = schedule?.timezone ?? coverage?.timezone ?? "UTC";

  const detailNotFound =
    detailQuery.isError &&
    detailQuery.error instanceof ApiError &&
    detailQuery.error.status === 404;

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={onCallSchedulesListPath(organizationId)}
          className="text-sm text-primary hover:underline"
        >
          ← Back to on-call schedules
        </Link>
      </div>

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

      {detailQuery.isPending && (
        <p className="text-sm text-muted-foreground" role="status">Loading schedule…</p>
      )}

      {detailQuery.isError && !detailNotFound && (
        <div className="space-y-2">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(detailQuery.error, "Failed to load schedule")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void detailQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {detailNotFound && (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <h1 className="text-lg font-medium">Schedule not found</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            This schedule may have been removed or you may not have access to it in this organization.
          </p>
          <Link
            href={onCallSchedulesListPath(organizationId)}
            className="mt-4 inline-block text-sm text-primary hover:underline"
          >
            Return to on-call list
          </Link>
        </div>
      )}

      {schedule && detail && (
        <>
          <header className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-3xl font-semibold tracking-tight">{schedule.name}</h1>
              <OnCallScheduleStatusBadge status={schedule.status} />
            </div>
            <p className="text-sm text-muted-foreground">{formatOnCallCadence(schedule)}</p>
          </header>

          {canManage && (
            <div className="flex flex-wrap gap-2">
              {schedule.status === "ACTIVE" && (
                <>
                  <Link
                    href={onCallScheduleEditPath(organizationId, scheduleId)}
                    className={buttonVariants({ variant: "outline", size: "sm" })}
                  >
                    Edit schedule
                  </Link>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={archiveMutation.isPending}
                    onClick={() => {
                      if (
                        !window.confirm(
                          "Archive this on-call schedule? It will stop providing active coverage until reactivated.",
                        )
                      ) {
                        return;
                      }
                      setLifecycleFeedback(null);
                      archiveMutation.mutate(undefined, {
                        onSuccess: async () => {
                          setLifecycleFeedback({
                            type: "success",
                            message: "Schedule archived. Coverage is no longer computed while archived.",
                          });
                          await detailQuery.refetch();
                        },
                        onError: (error) => {
                          setLifecycleFeedback({
                            type: "error",
                            message: getApiErrorMessage(error, "Failed to archive schedule"),
                          });
                        },
                      });
                    }}
                  >
                    {archiveMutation.isPending ? "Archiving…" : "Archive"}
                  </Button>
                </>
              )}
              {schedule.status === "ARCHIVED" && (
                <Button
                  type="button"
                  size="sm"
                  disabled={reactivateMutation.isPending}
                  onClick={() => {
                    setLifecycleFeedback(null);
                    reactivateMutation.mutate(undefined, {
                      onSuccess: async () => {
                        setLifecycleFeedback({
                          type: "success",
                          message: "Schedule reactivated. Coverage will refresh below.",
                        });
                        await detailQuery.refetch();
                        await coverageQuery.refetch();
                      },
                      onError: (error) => {
                        setLifecycleFeedback({
                          type: "error",
                          message: getApiErrorMessage(error, "Failed to reactivate schedule"),
                        });
                      },
                    });
                  }}
                >
                  {reactivateMutation.isPending ? "Reactivating…" : "Reactivate"}
                </Button>
              )}
            </div>
          )}

          {lifecycleFeedback && (
            <p
              className={
                lifecycleFeedback.type === "error"
                  ? "text-sm text-destructive"
                  : "text-sm text-muted-foreground"
              }
              role="status"
            >
              {lifecycleFeedback.message}
            </p>
          )}

          <section className="grid gap-4 rounded-lg border p-4 sm:grid-cols-2" aria-labelledby="schedule-config-heading">
            <h2 id="schedule-config-heading" className="sr-only">Schedule configuration</h2>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Timezone</p>
              <p className="text-sm">{schedule.timezone}</p>
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Handoff (local)</p>
              <p className="text-sm">{formatOnCallHandoffLocalTime(schedule.handoffLocalTime)}</p>
            </div>
            {schedule.rotationLengthDays === 7 && (
              <div>
                <p className="text-xs font-medium text-muted-foreground">Weekly handoff day</p>
                <p className="text-sm">{formatIsoDayOfWeek(schedule.handoffDayOfWeek) ?? "—"}</p>
              </div>
            )}
            <div>
              <p className="text-xs font-medium text-muted-foreground">Effective from</p>
              <p className="text-sm">{formatOnCallEffectiveFrom(schedule.effectiveFrom)}</p>
            </div>
            <div className="sm:col-span-2">
              <p className="text-xs font-medium text-muted-foreground">Scope</p>
              <p className="text-sm">
                {formatOnCallScopeLabel(schedule, scopeLookup)}
                {schedule.teamId && scopeLookup.teamNames?.get(schedule.teamId) ? (
                  <>
                    {" · "}
                    <Link
                      href={`/organizations/${organizationId}/teams/${schedule.teamId}`}
                      className="text-primary hover:underline"
                    >
                      View team
                    </Link>
                  </>
                ) : null}
                {schedule.serviceId && scopeLookup.serviceNames?.get(schedule.serviceId) ? (
                  <>
                    {" · "}
                    <Link
                      href={`/organizations/${organizationId}/services/${schedule.serviceId}`}
                      className="text-primary hover:underline"
                    >
                      View service
                    </Link>
                  </>
                ) : null}
              </p>
            </div>
          </section>

          <section className="space-y-3" aria-labelledby="participants-heading">
            <h2 id="participants-heading" className="text-base font-semibold">Rotation participants</h2>
            {participants.length === 0 ? (
              <p className="text-sm text-muted-foreground">No participants configured.</p>
            ) : (
              <ol className="space-y-2 text-sm">
                {participants.map((participant) => (
                  <li key={participant.userId} className="flex gap-3 rounded-md border px-3 py-2">
                    <span className="font-mono text-xs text-muted-foreground" aria-hidden="true">
                      {participant.position}
                    </span>
                    <span className="font-medium">{participant.displayName}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="space-y-4" aria-labelledby="coverage-heading">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 id="coverage-heading" className="text-base font-semibold">Coverage</h2>
                <p className="text-sm text-muted-foreground">
                  Times shown in schedule timezone ({timeZone}).
                </p>
              </div>
              {schedule.status === "ACTIVE" && (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={coverageQuery.isFetching}
                  onClick={() => void coverageQuery.refetch()}
                >
                  {coverageQuery.isFetching ? "Refreshing…" : "Refresh coverage"}
                </Button>
              )}
            </div>

            {schedule.status === "ARCHIVED" && (
              <p className="text-sm text-muted-foreground" role="status">
                This schedule is archived. Historical configuration is shown above; live coverage is
                not computed for archived schedules.
              </p>
            )}

            {schedule.status === "ACTIVE" && coverageQuery.isPending && (
              <p className="text-sm text-muted-foreground" role="status">Loading coverage…</p>
            )}

            {schedule.status === "ACTIVE" && coverageQuery.isError && (
              <p className="text-sm text-destructive" role="alert">
                {coverageUnavailableMessage(coverageQuery.error, schedule.status)}
              </p>
            )}

            {schedule.status === "ACTIVE" && coverage && (
              <div className="space-y-4">
                <div className="rounded-lg border p-4">
                  <h3 className="text-sm font-medium">Current primary</h3>
                  {coverage.current ? (
                    <div className="mt-2">
                      <p className="text-lg font-semibold">{coverage.current.displayName}</p>
                      <p className="text-sm text-muted-foreground">
                        Shift: {formatInstantInTimeZone(coverage.current.shiftStartsAt, timeZone)}
                        {" – "}
                        {formatInstantInTimeZone(coverage.current.shiftEndsAt, timeZone)}
                      </p>
                    </div>
                  ) : (
                    <p className="mt-2 text-sm text-muted-foreground" role="status">
                      No current coverage yet. The rotation starts on{" "}
                      {formatOnCallEffectiveFrom(schedule.effectiveFrom)} at{" "}
                      {formatOnCallHandoffLocalTime(schedule.handoffLocalTime)} ({timeZone}). Until
                      the first handoff, there is no primary on-call responder.
                    </p>
                  )}
                </div>

                <div>
                  <p className="text-xs font-medium text-muted-foreground">Next handoff</p>
                  <p className="text-sm">{formatInstantInTimeZone(coverage.nextHandoffAt, timeZone)}</p>
                </div>

                <div>
                  <h3 className="text-sm font-medium">Upcoming shifts</h3>
                  {coverage.upcoming.length === 0 ? (
                    <p className="mt-2 text-sm text-muted-foreground">No additional upcoming shifts.</p>
                  ) : (
                    <ul className="mt-2 space-y-2">
                      {coverage.upcoming.map((shift, index) => (
                        <ShiftRow
                          key={`${shift.userId}-${shift.shiftStartsAt}-${index}`}
                          shift={shift}
                          timeZone={timeZone}
                        />
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
