"use client";

import { useAuth } from "@/components/providers/auth-provider";
import {
  createIncidentComment,
  deleteIncidentComment,
  listIncidentComments,
  updateIncidentComment,
} from "@/lib/api/incident-comments";
import type {
  CreateIncidentCommentInput,
  UpdateIncidentCommentInput,
} from "@/types/incident";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export const INCIDENT_COMMENTS_QUERY_KEY = "incident-comments";

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

export type IncidentCommentsQueryParams = {
  organizationId: string;
  incidentId: string;
  page?: number;
  size?: number;
  enabled?: boolean;
};

export function incidentCommentsQueryKey(
  organizationId: string,
  incidentId: string,
  page: number,
  size: number,
  userId?: string,
) {
  return [INCIDENT_COMMENTS_QUERY_KEY, organizationId, incidentId, page, size, userId] as const;
}

async function invalidateIncidentComments(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  incidentId: string,
) {
  await queryClient.invalidateQueries({
    queryKey: [INCIDENT_COMMENTS_QUERY_KEY, organizationId, incidentId],
  });
}

export function useIncidentCommentsQuery({
  organizationId,
  incidentId,
  page = DEFAULT_PAGE,
  size = DEFAULT_SIZE,
  enabled = true,
}: IncidentCommentsQueryParams) {
  const { user } = useAuth();

  return useQuery({
    queryKey: incidentCommentsQueryKey(organizationId, incidentId, page, size, user?.id),
    queryFn: () => listIncidentComments(organizationId, incidentId, page, size),
    enabled: Boolean(enabled && organizationId && incidentId && user?.id),
  });
}

export function useCreateIncidentCommentMutation(organizationId: string, incidentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CreateIncidentCommentInput) =>
      createIncidentComment(organizationId, incidentId, input),
    onSuccess: async () => {
      await invalidateIncidentComments(queryClient, organizationId, incidentId);
    },
  });
}

export function useUpdateIncidentCommentMutation(organizationId: string, incidentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      commentId,
      input,
    }: {
      commentId: string;
      input: UpdateIncidentCommentInput;
    }) => updateIncidentComment(organizationId, incidentId, commentId, input),
    onSuccess: async () => {
      await invalidateIncidentComments(queryClient, organizationId, incidentId);
    },
  });
}

export function useDeleteIncidentCommentMutation(organizationId: string, incidentId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (commentId: string) =>
      deleteIncidentComment(organizationId, incidentId, commentId),
    onSuccess: async () => {
      await invalidateIncidentComments(queryClient, organizationId, incidentId);
    },
  });
}
