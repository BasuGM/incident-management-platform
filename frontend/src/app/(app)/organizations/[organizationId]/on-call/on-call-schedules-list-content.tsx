"use client";

import { OnCallScheduleStatusBadge } from "@/components/on-call/on-call-schedule-status-badge";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { OrganizationRoleHint } from "@/components/organization/organization-role-hint";
import { Button, buttonVariants } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/components/providers/auth-provider";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { listTeams } from "@/lib/api/teams";
import { useOnCallScheduleListQuery } from "@/lib/hooks/use-organization-on-call-schedules";
import {
  buildOnCallScopeNameMaps,
  formatOnCallCadence,
  formatOnCallScopeLabel,
  scheduleNeedsScopeNameLookup,
} from "@/lib/on-call-schedule-display";
import {
  ON_CALL_LIST_STATUS_FILTER_OPTIONS,
  onCallListEmptyMessage,
  onCallListFilterToApiStatus,
  onCallScheduleCreatePath,
  onCallScheduleDetailPath,
  type OnCallListStatusFilter,
} from "@/lib/on-call-schedule-list";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

export function OnCallSchedulesListContent({ organizationId }: { organizationId: string }) {
  const { user } = useAuth();
  const [statusFilter, setStatusFilter] = useState<OnCallListStatusFilter>("ACTIVE");

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;
  const apiStatus = onCallListFilterToApiStatus(statusFilter);

  const schedulesQuery = useOnCallScheduleListQuery({
    organizationId,
    status: apiStatus,
    enabled: Boolean(organizationId && user?.id && organization),
  });

  const schedules = schedulesQuery.data ?? [];
  const needsScopeLookup = scheduleNeedsScopeNameLookup(schedules);

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

  const isInitialLoading =
    Boolean(organizationId && user?.id && organization) &&
    schedulesQuery.isPending &&
    schedulesQuery.data === undefined;

  const showStatusColumn = statusFilter === "ALL";
  const emptyCopy = onCallListEmptyMessage(statusFilter, canManage);

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/organizations/${organizationId}`}
          className="text-sm text-primary hover:underline"
        >
          ← Back to organization
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">On-call</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          View rotation schedules and who is on call for this organization. Coverage is computed
          from each schedule&apos;s timezone and handoff rules.
        </p>
      </div>

      {organizationQuery.isError && (
        <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(organizationQuery.error, "Failed to load organization")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void organizationQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {organization && (
        <>
          <OrganizationNav organizationId={organizationId} canManage={canManage} />
          <OrganizationRoleHint organizationRole={organization.currentUserRole} />
        </>
      )}

      {organization && (
        <div className="flex flex-wrap items-end gap-4">
          {canManage && (
            <Link
              href={onCallScheduleCreatePath(organizationId)}
              className={buttonVariants({ variant: "default" })}
            >
              Create schedule
            </Link>
          )}
          <div className="space-y-2">
            <Label htmlFor="on-call-status-filter">Status</Label>
            <select
              id="on-call-status-filter"
              className="flex h-10 min-w-[12rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as OnCallListStatusFilter)}
              aria-label="On-call schedule status filter"
            >
              {ON_CALL_LIST_STATUS_FILTER_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {isInitialLoading && (
        <p className="text-sm text-muted-foreground" role="status">Loading on-call schedules…</p>
      )}

      {schedulesQuery.isError && (
        <div className="space-y-2">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(schedulesQuery.error, "Failed to load on-call schedules")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void schedulesQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {!isInitialLoading && !schedulesQuery.isError && schedules.length === 0 && organization && (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <h2 className="text-lg font-medium">{emptyCopy.title}</h2>
          <p className="mt-2 text-sm text-muted-foreground">{emptyCopy.description}</p>
        </div>
      )}

      {schedules.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b bg-muted/40 text-muted-foreground">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium">Schedule</th>
                <th scope="col" className="px-3 py-2 font-medium">Cadence</th>
                <th scope="col" className="px-3 py-2 font-medium">Timezone</th>
                <th scope="col" className="px-3 py-2 font-medium">Scope</th>
                <th scope="col" className="px-3 py-2 font-medium">Participants</th>
                {showStatusColumn && (
                  <th scope="col" className="px-3 py-2 font-medium">Status</th>
                )}
                <th scope="col" className="px-3 py-2 font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {schedules.map((schedule) => (
                <tr key={schedule.id} className="border-b last:border-b-0">
                  <td className="px-3 py-2 font-medium">
                    <Link
                      href={onCallScheduleDetailPath(organizationId, schedule.id)}
                      className="text-primary hover:underline"
                    >
                      {schedule.name}
                    </Link>
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">{formatOnCallCadence(schedule)}</td>
                  <td className="px-3 py-2 text-muted-foreground">{schedule.timezone}</td>
                  <td className="px-3 py-2 text-muted-foreground">
                    {formatOnCallScopeLabel(schedule, scopeLookup)}
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">{schedule.participantCount}</td>
                  {showStatusColumn && (
                    <td className="px-3 py-2">
                      <OnCallScheduleStatusBadge status={schedule.status} />
                    </td>
                  )}
                  <td className="px-3 py-2">
                    <Link
                      href={onCallScheduleDetailPath(organizationId, schedule.id)}
                      className="text-primary hover:underline"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

    </div>
  );
}
