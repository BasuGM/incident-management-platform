"use client";

import { useAuth } from "@/components/providers/auth-provider";
import {
  archiveOnCallSchedule,
  createOnCallSchedule,
  getOnCallCoverage,
  getOnCallSchedule,
  listOnCallSchedules,
  reactivateOnCallSchedule,
  updateOnCallSchedule,
} from "@/lib/api/on-call-schedules";
import type {
  CreateOnCallScheduleInput,
  GetOnCallCoverageParams,
  ListOnCallSchedulesParams,
  OnCallScheduleListStatus,
  UpdateOnCallScheduleBody,
} from "@/types/on-call-schedule";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export const ON_CALL_SCHEDULES_QUERY_KEY = "on-call-schedules";

export function onCallScheduleListQueryKey(
  organizationId: string,
  status: OnCallScheduleListStatus | "DEFAULT",
  userId?: string,
) {
  return [ON_CALL_SCHEDULES_QUERY_KEY, organizationId, "list", status, userId] as const;
}

export function onCallScheduleDetailQueryKey(
  organizationId: string,
  scheduleId: string,
  userId?: string,
) {
  return [ON_CALL_SCHEDULES_QUERY_KEY, organizationId, "detail", scheduleId, userId] as const;
}

export function onCallCoverageQueryKey(
  organizationId: string,
  scheduleId: string,
  at: string | "DEFAULT",
  upcoming: number | "DEFAULT",
  userId?: string,
) {
  return [
    ON_CALL_SCHEDULES_QUERY_KEY,
    organizationId,
    "coverage",
    scheduleId,
    at,
    upcoming,
    userId,
  ] as const;
}

export async function invalidateOnCallScheduleQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  scheduleId?: string,
) {
  await queryClient.invalidateQueries({
    queryKey: [ON_CALL_SCHEDULES_QUERY_KEY, organizationId],
  });
  if (scheduleId) {
    await queryClient.invalidateQueries({
      queryKey: [ON_CALL_SCHEDULES_QUERY_KEY, organizationId, "detail", scheduleId],
    });
    await queryClient.invalidateQueries({
      queryKey: [ON_CALL_SCHEDULES_QUERY_KEY, organizationId, "coverage", scheduleId],
    });
  }
}

export type UseOnCallScheduleListQueryParams = {
  organizationId: string;
  status?: OnCallScheduleListStatus;
  enabled?: boolean;
};

export function useOnCallScheduleListQuery({
  organizationId,
  status,
  enabled = true,
}: UseOnCallScheduleListQueryParams) {
  const { user } = useAuth();
  const statusKey = status ?? "DEFAULT";

  return useQuery({
    queryKey: onCallScheduleListQueryKey(organizationId, statusKey, user?.id),
    queryFn: () =>
      listOnCallSchedules(
        organizationId,
        status !== undefined ? { status } satisfies ListOnCallSchedulesParams : {},
      ),
    enabled: Boolean(enabled && organizationId && user?.id),
  });
}

export type UseOnCallScheduleDetailQueryParams = {
  organizationId: string;
  scheduleId: string;
  enabled?: boolean;
};

export function useOnCallScheduleDetailQuery({
  organizationId,
  scheduleId,
  enabled = true,
}: UseOnCallScheduleDetailQueryParams) {
  const { user } = useAuth();

  return useQuery({
    queryKey: onCallScheduleDetailQueryKey(organizationId, scheduleId, user?.id),
    queryFn: () => getOnCallSchedule(organizationId, scheduleId),
    enabled: Boolean(enabled && organizationId && scheduleId && user?.id),
  });
}

export type UseOnCallCoverageQueryParams = {
  organizationId: string;
  scheduleId: string;
  at?: string;
  upcoming?: number;
  enabled?: boolean;
};

export function useOnCallCoverageQuery({
  organizationId,
  scheduleId,
  at,
  upcoming,
  enabled = true,
}: UseOnCallCoverageQueryParams) {
  const { user } = useAuth();
  const atKey = at ?? "DEFAULT";
  const upcomingKey = upcoming ?? "DEFAULT";

  return useQuery({
    queryKey: onCallCoverageQueryKey(organizationId, scheduleId, atKey, upcomingKey, user?.id),
    queryFn: () =>
      getOnCallCoverage(organizationId, scheduleId, {
        at,
        upcoming,
      } satisfies GetOnCallCoverageParams),
    enabled: Boolean(enabled && organizationId && scheduleId && user?.id),
  });
}

function useOnCallScheduleMutationContext(organizationId: string, scheduleId: string) {
  const queryClient = useQueryClient();

  const invalidate = async () => {
    await invalidateOnCallScheduleQueries(queryClient, organizationId, scheduleId);
  };

  return { queryClient, invalidate };
}

export function useCreateOnCallScheduleMutation(organizationId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CreateOnCallScheduleInput) => createOnCallSchedule(organizationId, input),
    onSuccess: async (data) => {
      await invalidateOnCallScheduleQueries(queryClient, organizationId, data.schedule.id);
    },
  });
}

export function useUpdateOnCallScheduleMutation(organizationId: string, scheduleId: string) {
  const { invalidate } = useOnCallScheduleMutationContext(organizationId, scheduleId);

  return useMutation({
    mutationFn: (body: UpdateOnCallScheduleBody) =>
      updateOnCallSchedule(organizationId, scheduleId, body),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useArchiveOnCallScheduleMutation(organizationId: string, scheduleId: string) {
  const { invalidate } = useOnCallScheduleMutationContext(organizationId, scheduleId);

  return useMutation({
    mutationFn: () => archiveOnCallSchedule(organizationId, scheduleId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useReactivateOnCallScheduleMutation(organizationId: string, scheduleId: string) {
  const { invalidate } = useOnCallScheduleMutationContext(organizationId, scheduleId);

  return useMutation({
    mutationFn: () => reactivateOnCallSchedule(organizationId, scheduleId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}
