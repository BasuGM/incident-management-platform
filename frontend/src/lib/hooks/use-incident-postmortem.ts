"use client";

import { useAuth } from "@/components/providers/auth-provider";
import {
  archiveIncidentPostmortem,
  createIncidentPostmortem,
  deleteIncidentPostmortem,
  getIncidentPostmortem,
  listOrganizationPostmortems,
  publishIncidentPostmortem,
  type ListOrganizationPostmortemsParams,
  unarchiveIncidentPostmortem,
  unpublishIncidentPostmortem,
  updateIncidentPostmortem,
} from "@/lib/api/incident-postmortems";
import type {
  CreateIncidentPostmortemInput,
  OrganizationPostmortemListStatus,
  UpdateIncidentPostmortemBody,
} from "@/types/incident";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export const INCIDENT_POSTMORTEM_QUERY_KEY = "incident-postmortem";
export const ORGANIZATION_POSTMORTEMS_QUERY_KEY = "organization-postmortems";

export type IncidentPostmortemQueryParams = {
  organizationId: string;
  incidentId: string;
  enabled?: boolean;
};

export type OrganizationPostmortemsQueryParams = {
  organizationId: string;
  page?: number;
  size?: number;
  status?: OrganizationPostmortemListStatus;
  enabled?: boolean;
};

export function incidentPostmortemQueryKey(
  organizationId: string,
  incidentId: string,
  userId?: string,
) {
  return [INCIDENT_POSTMORTEM_QUERY_KEY, organizationId, incidentId, userId] as const;
}

export function organizationPostmortemsQueryKey(
  organizationId: string,
  page: number,
  size: number,
  status: OrganizationPostmortemListStatus | "DEFAULT",
  userId?: string,
) {
  return [ORGANIZATION_POSTMORTEMS_QUERY_KEY, organizationId, page, size, status, userId] as const;
}

export async function invalidateIncidentPostmortemQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  incidentId: string,
) {
  await queryClient.invalidateQueries({
    queryKey: [INCIDENT_POSTMORTEM_QUERY_KEY, organizationId, incidentId],
  });
}

export async function invalidateOrganizationPostmortemListQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
) {
  await queryClient.invalidateQueries({
    queryKey: [ORGANIZATION_POSTMORTEMS_QUERY_KEY, organizationId],
  });
}

export function useIncidentPostmortemQuery({
  organizationId,
  incidentId,
  enabled = true,
}: IncidentPostmortemQueryParams) {
  const { user } = useAuth();

  return useQuery({
    queryKey: incidentPostmortemQueryKey(organizationId, incidentId, user?.id),
    queryFn: () => getIncidentPostmortem(organizationId, incidentId),
    enabled: Boolean(enabled && organizationId && incidentId && user?.id),
  });
}

export function useOrganizationPostmortemsQuery({
  organizationId,
  page = 0,
  size = 20,
  status,
  enabled = true,
}: OrganizationPostmortemsQueryParams) {
  const { user } = useAuth();
  const statusKey = status ?? "DEFAULT";

  return useQuery({
    queryKey: organizationPostmortemsQueryKey(organizationId, page, size, statusKey, user?.id),
    queryFn: () =>
      listOrganizationPostmortems(organizationId, {
        page,
        size,
        status,
      } satisfies ListOrganizationPostmortemsParams),
    enabled: Boolean(enabled && organizationId && user?.id),
  });
}

function usePostmortemMutationContext(organizationId: string, incidentId: string) {
  const queryClient = useQueryClient();

  const invalidate = async () => {
    await invalidateIncidentPostmortemQueries(queryClient, organizationId, incidentId);
    await invalidateOrganizationPostmortemListQueries(queryClient, organizationId);
  };

  return { queryClient, invalidate };
}

export function useCreateIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { user } = useAuth();
  const { queryClient } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: (input?: CreateIncidentPostmortemInput) =>
      createIncidentPostmortem(organizationId, incidentId, input),
    onSuccess: async (data) => {
      queryClient.setQueryData(
        incidentPostmortemQueryKey(organizationId, incidentId, user?.id),
        data,
      );
      await invalidateOrganizationPostmortemListQueries(queryClient, organizationId);
      return data;
    },
  });
}

export function useUpdateIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { user } = useAuth();
  const { queryClient, invalidate } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: (body: UpdateIncidentPostmortemBody) =>
      updateIncidentPostmortem(organizationId, incidentId, body),
    onSuccess: async (data) => {
      queryClient.setQueryData(
        incidentPostmortemQueryKey(organizationId, incidentId, user?.id),
        data,
      );
      await invalidate();
    },
  });
}

export function usePublishIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { invalidate } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: () => publishIncidentPostmortem(organizationId, incidentId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useUnpublishIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { invalidate } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: () => unpublishIncidentPostmortem(organizationId, incidentId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useArchiveIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { invalidate } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: () => archiveIncidentPostmortem(organizationId, incidentId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useUnarchiveIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { invalidate } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: () => unarchiveIncidentPostmortem(organizationId, incidentId),
    onSuccess: async () => {
      await invalidate();
    },
  });
}

export function useDeleteIncidentPostmortemMutation(organizationId: string, incidentId: string) {
  const { queryClient } = usePostmortemMutationContext(organizationId, incidentId);

  return useMutation({
    mutationFn: () => deleteIncidentPostmortem(organizationId, incidentId),
    onSuccess: async () => {
      queryClient.removeQueries({
        queryKey: [INCIDENT_POSTMORTEM_QUERY_KEY, organizationId, incidentId],
      });
      await invalidateOrganizationPostmortemListQueries(queryClient, organizationId);
    },
  });
}
