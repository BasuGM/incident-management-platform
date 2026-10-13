"use client";

import { OnCallScheduleForm } from "@/components/on-call/on-call-schedule-form";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { useAuth } from "@/components/providers/auth-provider";
import { ApiError } from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { listTeamMembers, listTeams } from "@/lib/api/teams";
import { useCreateOnCallScheduleMutation } from "@/lib/hooks/use-organization-on-call-schedules";
import {
  buildCreateOnCallScheduleInput,
  createEmptyOnCallScheduleFormValues,
  validateOnCallScheduleForm,
  type OnCallScheduleFormFieldErrors,
  type OnCallScheduleFormValues,
} from "@/lib/on-call-schedule-form";
import { onCallSchedulesListPath } from "@/lib/on-call-schedule-list";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

export default function CreateOnCallSchedulePage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const router = useRouter();
  const { user } = useAuth();

  const [values, setValues] = useState<OnCallScheduleFormValues>(() =>
    createEmptyOnCallScheduleFormValues(),
  );
  const [fieldErrors, setFieldErrors] = useState<OnCallScheduleFormFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

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
    queryKey: ["team-members", values.teamId],
    queryFn: () => listTeamMembers(values.teamId),
    enabled: Boolean(canManage && values.scopeMode === "TEAM" && values.teamId),
  });

  const createMutation = useCreateOnCallScheduleMutation(organizationId);

  if (organization && !canManage) {
    return (
      <div className="space-y-4">
        <Link href={onCallSchedulesListPath(organizationId)} className="text-sm text-primary hover:underline">
          ← Back to on-call schedules
        </Link>
        <p className="text-sm text-muted-foreground">
          You do not have permission to create on-call schedules.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <Link href={onCallSchedulesListPath(organizationId)} className="text-sm text-primary hover:underline">
          ← Back to on-call schedules
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Create on-call schedule</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Define rotation cadence, scope, and primary participants for this organization.
        </p>
      </div>

      {organization && <OrganizationNav organizationId={organizationId} canManage={canManage} />}

      <OnCallScheduleForm
        organizationId={organizationId}
        values={values}
        onChange={setValues}
        fieldErrors={fieldErrors}
        formError={formError}
        submitLabel="Create schedule"
        cancelHref={onCallSchedulesListPath(organizationId)}
        isPending={createMutation.isPending}
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
          const input = buildCreateOnCallScheduleInput(values);
          createMutation.mutate(input, {
            onSuccess: (detail) => {
              router.push(`/organizations/${organizationId}/on-call/${detail.schedule.id}`);
            },
            onError: (error) => {
              if (error instanceof ApiError && error.status === 403) {
                setFormError("You do not have permission to create schedules in this organization.");
                return;
              }
              setFormError(getApiErrorMessage(error, "Failed to create schedule"));
            },
          });
        }}
      />
    </div>
  );
}
