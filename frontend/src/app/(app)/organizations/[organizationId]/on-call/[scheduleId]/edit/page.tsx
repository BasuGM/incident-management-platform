"use client";

import { OnCallScheduleForm } from "@/components/on-call/on-call-schedule-form";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/components/providers/auth-provider";
import { ApiError } from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { listTeamMembers, listTeams } from "@/lib/api/teams";
import {
  useOnCallScheduleDetailQuery,
  useUpdateOnCallScheduleMutation,
} from "@/lib/hooks/use-organization-on-call-schedules";
import {
  buildUpdateOnCallScheduleBody,
  detailToFormValues,
  validateOnCallScheduleForm,
  type OnCallScheduleFormFieldErrors,
  type OnCallScheduleFormValues,
} from "@/lib/on-call-schedule-form";
import { onCallScheduleDetailPath } from "@/lib/on-call-schedule-list";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

export default function EditOnCallSchedulePage() {
  const params = useParams<{ organizationId: string; scheduleId: string }>();
  const { organizationId, scheduleId } = params;
  const router = useRouter();
  const { user } = useAuth();

  const [values, setValues] = useState<OnCallScheduleFormValues | null>(null);
  const initialRef = useRef<OnCallScheduleFormValues | null>(null);
  const [fieldErrors, setFieldErrors] = useState<OnCallScheduleFormFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

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
  const schedule = detailQuery.data?.schedule;

  useEffect(() => {
    if (!detailQuery.data || values) {
      return;
    }
    const next = detailToFormValues(detailQuery.data);
    initialRef.current = next;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- hydrate edit form once from server detail
    setValues(next);
  }, [detailQuery.data, values]);

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
    enabled: Boolean(organization && canManage),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
    enabled: Boolean(organization && canManage),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
    enabled: Boolean(organization && canManage),
  });

  const teamMembersQuery = useQuery({
    queryKey: ["team-members", values?.teamId],
    queryFn: () => listTeamMembers(values!.teamId),
    enabled: Boolean(canManage && values?.scopeMode === "TEAM" && values.teamId),
  });

  const updateMutation = useUpdateOnCallScheduleMutation(organizationId, scheduleId);

  if (organization && !canManage) {
    return (
      <div className="space-y-4">
        <Link
          href={onCallScheduleDetailPath(organizationId, scheduleId)}
          className="text-sm text-primary hover:underline"
        >
          ← Back to schedule
        </Link>
        <p className="text-sm text-muted-foreground">
          You do not have permission to edit on-call schedules.
        </p>
      </div>
    );
  }

  if (schedule?.status === "ARCHIVED") {
    return (
      <div className="space-y-4">
        <Link
          href={onCallScheduleDetailPath(organizationId, scheduleId)}
          className="text-sm text-primary hover:underline"
        >
          ← Back to schedule
        </Link>
        <p className="text-sm text-muted-foreground">
          Archived schedules cannot be edited. Reactivate the schedule first, then update its
          configuration.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={onCallScheduleDetailPath(organizationId, scheduleId)}
          className="text-sm text-primary hover:underline"
        >
          ← Back to schedule
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Edit on-call schedule</h1>
      </div>

      {organization && <OrganizationNav organizationId={organizationId} canManage={canManage} />}

      {detailQuery.isPending && (
        <p className="text-sm text-muted-foreground" role="status">Loading schedule…</p>
      )}

      {detailQuery.isError && (
        <div className="space-y-2">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(detailQuery.error, "Failed to load schedule")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void detailQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {values && (
        <OnCallScheduleForm
          organizationId={organizationId}
          values={values}
          onChange={setValues}
          fieldErrors={fieldErrors}
          formError={formError}
          submitLabel="Save changes"
          cancelHref={onCallScheduleDetailPath(organizationId, scheduleId)}
          isPending={updateMutation.isPending}
          teams={teamsQuery.data}
          services={servicesQuery.data}
          teamsLoading={teamsQuery.isPending}
          servicesLoading={servicesQuery.isPending}
          teamsError={teamsQuery.isError}
          servicesError={servicesQuery.isError}
          orgMembers={membersQuery.data}
          teamMembers={teamMembersQuery.data}
          teamMembersLoading={teamMembersQuery.isPending}
          teamMembersError={teamMembersQuery.isError}
          onSubmit={() => {
            setFormError(null);
            const errors = validateOnCallScheduleForm(values);
            setFieldErrors(errors);
            if (Object.keys(errors).length > 0) {
              return;
            }
            const initial = initialRef.current;
            if (!initial) {
              return;
            }
            const body = buildUpdateOnCallScheduleBody(initial, values);
            if (Object.keys(body).length === 0) {
              router.push(onCallScheduleDetailPath(organizationId, scheduleId));
              return;
            }
            updateMutation.mutate(body, {
              onSuccess: () => {
                router.push(onCallScheduleDetailPath(organizationId, scheduleId));
              },
              onError: (error) => {
                if (error instanceof ApiError && error.status === 403) {
                  setFormError("You do not have permission to update this schedule.");
                  return;
                }
                setFormError(getApiErrorMessage(error, "Failed to update schedule"));
              },
            });
          }}
        />
      )}
    </div>
  );
}
