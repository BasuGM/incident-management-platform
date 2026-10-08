"use client";

import { IncidentCommentComposer } from "@/components/incident/incident-comment-composer";
import { IncidentCommentItem } from "@/components/incident/incident-comment-item";
import { Button } from "@/components/ui/button";
import { getApiErrorMessage } from "@/lib/api/errors";
import { canCreateComment } from "@/lib/comment-rbac";
import { listIncidentComments } from "@/lib/api/incident-comments";
import {
  incidentCommentsQueryKey,
  useCreateIncidentCommentMutation,
  useDeleteIncidentCommentMutation,
  useUpdateIncidentCommentMutation,
} from "@/lib/hooks/use-incident-comments";
import type { IncidentComment } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";
import { useAuth } from "@/components/providers/auth-provider";
import { useQueries } from "@tanstack/react-query";
import { useMemo, useState } from "react";

const COMMENT_PAGE_SIZE = 20;

type IncidentCommentsProps = {
  organizationId: string;
  incidentId: string;
  organizationRole: OrganizationRole;
  incidentWritable: boolean;
};

function sortComments(comments: IncidentComment[]) {
  return [...comments].sort((a, b) => {
    const byTime = a.createdAt.localeCompare(b.createdAt);
    if (byTime !== 0) {
      return byTime;
    }
    return a.id.localeCompare(b.id);
  });
}

function mergeCommentPages(pages: IncidentComment[][]) {
  const byId = new Map<string, IncidentComment>();
  for (const page of pages) {
    for (const comment of page) {
      byId.set(comment.id, comment);
    }
  }
  return sortComments([...byId.values()]);
}

export function IncidentComments({
  organizationId,
  incidentId,
  organizationRole,
  incidentWritable,
}: IncidentCommentsProps) {
  const { user } = useAuth();
  const [pagesToLoad, setPagesToLoad] = useState([0]);
  const [createError, setCreateError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [activeCommentId, setActiveCommentId] = useState<string | null>(null);

  const enabled = Boolean(organizationId && incidentId && user?.id);

  const commentQueries = useQueries({
    queries: pagesToLoad.map((page) => ({
      queryKey: incidentCommentsQueryKey(
        organizationId,
        incidentId,
        page,
        COMMENT_PAGE_SIZE,
        user?.id,
      ),
      queryFn: () => listIncidentComments(organizationId, incidentId, page, COMMENT_PAGE_SIZE),
      enabled,
    })),
  });

  const firstQuery = commentQueries[0];
  const isInitialLoading = enabled && firstQuery?.isPending && !firstQuery?.data;
  const loadError = firstQuery?.isError ? firstQuery.error : null;

  const comments = useMemo(
    () => mergeCommentPages(commentQueries.map((query) => query.data?.content ?? [])),
    [commentQueries],
  );

  const totalPages = firstQuery?.data?.totalPages ?? 0;
  const lastLoadedPage = pagesToLoad[pagesToLoad.length - 1] ?? 0;
  const hasMore = totalPages > 0 && lastLoadedPage + 1 < totalPages;
  const isLoadingMore = commentQueries.some((query) => query.isFetching && query.data);

  const createMutation = useCreateIncidentCommentMutation(organizationId, incidentId);
  const updateMutation = useUpdateIncidentCommentMutation(organizationId, incidentId);
  const deleteMutation = useDeleteIncidentCommentMutation(organizationId, incidentId);

  const showComposer = canCreateComment(organizationRole, incidentWritable);

  function handleCreate(body: string) {
    setCreateError(null);
    createMutation.mutate(
      { body },
      {
        onSuccess: () => setCreateError(null),
        onError: (error) => {
          setCreateError(getApiErrorMessage(error, "Failed to post comment"));
        },
      },
    );
  }

  function handleUpdate(commentId: string, body: string) {
    setMutationError(null);
    setActiveCommentId(commentId);
    updateMutation.mutate(
      { commentId, input: { body } },
      {
        onSuccess: () => {
          setMutationError(null);
          setActiveCommentId(null);
        },
        onError: (error) => {
          setMutationError(getApiErrorMessage(error, "Failed to update comment"));
        },
      },
    );
  }

  function handleDelete(commentId: string) {
    setMutationError(null);
    setActiveCommentId(commentId);
    deleteMutation.mutate(commentId, {
      onSuccess: () => {
        setMutationError(null);
        setActiveCommentId(null);
      },
      onError: (error) => {
        setMutationError(getApiErrorMessage(error, "Failed to delete comment"));
      },
    });
  }

  return (
    <section className="space-y-4" aria-labelledby="incident-comments-heading">
      <h2 id="incident-comments-heading" className="text-xl font-semibold tracking-tight">
        Comments
      </h2>

      {showComposer && (
        <IncidentCommentComposer
          isSubmitting={createMutation.isPending}
          errorMessage={createError}
          onSubmit={handleCreate}
        />
      )}

      {isInitialLoading && (
        <p className="text-sm text-muted-foreground" role="status">Loading comments…</p>
      )}

      {loadError && (
        <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(loadError, "Failed to load comments")}
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void firstQuery?.refetch()}
          >
            Retry
          </Button>
        </div>
      )}

      {!isInitialLoading && !loadError && comments.length === 0 && (
        <p className="text-sm text-muted-foreground">
          {organizationRole === "VIEWER"
            ? "Comments will appear here."
            : "No comments yet. Start the conversation about this incident."}
        </p>
      )}

      {!loadError && comments.length > 0 && (
        <ul className="space-y-3" aria-label="Incident comments">
          {comments.map((comment) => (
            <IncidentCommentItem
              key={comment.id}
              comment={comment}
              organizationRole={organizationRole}
              currentUserId={user?.id ?? ""}
              incidentWritable={incidentWritable}
              isUpdating={updateMutation.isPending && activeCommentId === comment.id}
              isDeleting={deleteMutation.isPending && activeCommentId === comment.id}
              mutationError={activeCommentId === comment.id ? mutationError : null}
              onSave={handleUpdate}
              onDelete={handleDelete}
            />
          ))}
        </ul>
      )}

      {hasMore && !loadError && (
        <Button
          type="button"
          variant="outline"
          disabled={isLoadingMore}
          onClick={() => {
            const nextPage = lastLoadedPage + 1;
            if (!pagesToLoad.includes(nextPage)) {
              setPagesToLoad((current) => [...current, nextPage]);
            }
          }}
        >
          {isLoadingMore ? "Loading…" : "Load more"}
        </Button>
      )}
    </section>
  );
}
