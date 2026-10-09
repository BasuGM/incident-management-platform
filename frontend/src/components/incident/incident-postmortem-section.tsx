"use client";

import { IncidentPostmortemDisplay } from "@/components/incident/incident-postmortem-display";
import { IncidentPostmortemEditor } from "@/components/incident/incident-postmortem-editor";
import { IncidentPostmortemStatusBadge } from "@/components/incident/incident-postmortem-status-badge";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { useAuth } from "@/components/providers/auth-provider";
import { getApiErrorMessage } from "@/lib/api/errors";
import {
  buildPostmortemUpdateBody,
  getPublishValidationError,
  isPostmortemMutationPending,
  postmortemToFormValues,
  validatePostmortemForm,
  type PostmortemFormValues,
} from "@/lib/postmortem-form";
import { getPostmortemActionVisibility } from "@/lib/postmortem-rbac";
import { resolvePostmortemViewState } from "@/lib/postmortem-ui-state";
import {
  useArchiveIncidentPostmortemMutation,
  useCreateIncidentPostmortemMutation,
  useDeleteIncidentPostmortemMutation,
  useIncidentPostmortemQuery,
  usePublishIncidentPostmortemMutation,
  useUnarchiveIncidentPostmortemMutation,
  useUnpublishIncidentPostmortemMutation,
  useUpdateIncidentPostmortemMutation,
} from "@/lib/hooks/use-incident-postmortem";
import type { IncidentStatus } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";
import { useState } from "react";

type IncidentPostmortemSectionProps = {
  organizationId: string;
  incidentId: string;
  organizationRole: OrganizationRole;
  incidentStatus: IncidentStatus;
};

export function IncidentPostmortemSection({
  organizationId,
  incidentId,
  organizationRole,
  incidentStatus,
}: IncidentPostmortemSectionProps) {
  const { user } = useAuth();
  const incidentResolved = incidentStatus === "RESOLVED";
  const currentUserId = user?.id ?? "";

  const postmortemQuery = useIncidentPostmortemQuery({ organizationId, incidentId });

  const createMutation = useCreateIncidentPostmortemMutation(organizationId, incidentId);
  const updateMutation = useUpdateIncidentPostmortemMutation(organizationId, incidentId);
  const publishMutation = usePublishIncidentPostmortemMutation(organizationId, incidentId);
  const unpublishMutation = useUnpublishIncidentPostmortemMutation(organizationId, incidentId);
  const archiveMutation = useArchiveIncidentPostmortemMutation(organizationId, incidentId);
  const unarchiveMutation = useUnarchiveIncidentPostmortemMutation(organizationId, incidentId);
  const deleteMutation = useDeleteIncidentPostmortemMutation(organizationId, incidentId);

  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState<PostmortemFormValues | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const viewState = resolvePostmortemViewState({
    isPending: postmortemQuery.isPending,
    isFetching: postmortemQuery.isFetching,
    error: postmortemQuery.isError ? postmortemQuery.error : null,
    data: postmortemQuery.data,
    role: organizationRole,
    incidentResolved,
  });

  const postmortem = viewState.kind === "content" ? viewState.postmortem : postmortemQuery.data;

  const actions = getPostmortemActionVisibility(
    organizationRole,
    postmortem,
    currentUserId,
    incidentResolved,
  );

  const mutationPending = isPostmortemMutationPending({
    create: createMutation.isPending,
    update: updateMutation.isPending,
    publish: publishMutation.isPending,
    unpublish: unpublishMutation.isPending,
    archive: archiveMutation.isPending,
    unarchive: unarchiveMutation.isPending,
    delete: deleteMutation.isPending,
  });

  function startEditing() {
    if (!postmortem) {
      return;
    }
    setDraft(postmortemToFormValues(postmortem));
    setFormError(null);
    setActionError(null);
    setIsEditing(true);
  }

  function cancelEditing() {
    if (postmortem) {
      setDraft(postmortemToFormValues(postmortem));
    }
    setFormError(null);
    setIsEditing(false);
  }

  function handleCreate() {
    setActionError(null);
    createMutation.mutate(undefined, {
      onSuccess: (created) => {
        setDraft(postmortemToFormValues(created));
        setIsEditing(true);
        setActionError(null);
      },
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to create postmortem"));
      },
    });
  }

  function handleSave() {
    if (!postmortem || !draft) {
      return;
    }
    const validationError = validatePostmortemForm(draft);
    if (validationError) {
      setFormError(validationError);
      return;
    }
    const body = buildPostmortemUpdateBody(postmortem, draft);
    if (Object.keys(body).length === 0) {
      setFormError("No changes to save.");
      return;
    }
    setFormError(null);
    updateMutation.mutate(body, {
      onSuccess: (updated) => {
        setDraft(postmortemToFormValues(updated));
        setIsEditing(false);
        setFormError(null);
      },
      onError: (error) => {
        setFormError(getApiErrorMessage(error, "Failed to save postmortem"));
      },
    });
  }

  function handlePublish() {
    if (!draft && postmortem) {
      const publishError = getPublishValidationError(postmortemToFormValues(postmortem));
      if (publishError) {
        setActionError(publishError);
        return;
      }
    } else if (draft) {
      const publishError = getPublishValidationError(draft);
      if (publishError) {
        setActionError(publishError);
        return;
      }
    }
    if (
      !window.confirm(
        "Publish this postmortem? Published content cannot be edited until it is unpublished.",
      )
    ) {
      return;
    }
    setActionError(null);
    publishMutation.mutate(undefined, {
      onSuccess: () => {
        setIsEditing(false);
        setActionError(null);
      },
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to publish postmortem"));
      },
    });
  }

  function handleUnpublish() {
    if (!window.confirm("Unpublish this postmortem? It will return to draft for editing.")) {
      return;
    }
    setActionError(null);
    unpublishMutation.mutate(undefined, {
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to unpublish postmortem"));
      },
    });
  }

  function handleArchive() {
    if (!window.confirm("Archive this postmortem? It will be hidden from the default library list.")) {
      return;
    }
    setActionError(null);
    archiveMutation.mutate(undefined, {
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to archive postmortem"));
      },
    });
  }

  function handleUnarchive() {
    setActionError(null);
    unarchiveMutation.mutate(undefined, {
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to unarchive postmortem"));
      },
    });
  }

  function handleDelete() {
    if (!window.confirm("Delete this draft postmortem? This cannot be undone.")) {
      return;
    }
    setActionError(null);
    deleteMutation.mutate(undefined, {
      onSuccess: () => {
        setIsEditing(false);
        setDraft(null);
      },
      onError: (error) => {
        setActionError(getApiErrorMessage(error, "Failed to delete postmortem"));
      },
    });
  }

  const showDraftEditor =
    postmortem?.status === "DRAFT" && isEditing && draft && actions.showEdit;

  return (
    <section className="space-y-4" aria-labelledby="incident-postmortem-heading">
      <div className="flex flex-wrap items-center gap-2">
        <h2 id="incident-postmortem-heading" className="text-xl font-semibold tracking-tight">
          Postmortem
        </h2>
        {postmortem && <IncidentPostmortemStatusBadge status={postmortem.status} />}
      </div>

      {viewState.kind === "loading" && (
        <p className="text-sm text-muted-foreground" role="status">Loading postmortem…</p>
      )}

      {viewState.kind === "error" && (
        <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm text-destructive">{viewState.message}</p>
          <Button type="button" variant="outline" size="sm" onClick={() => void postmortemQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {viewState.kind === "empty" && (
        <div className="rounded-lg border border-dashed p-4 space-y-3">
          <p className="text-sm text-muted-foreground">{viewState.hint}</p>
          {viewState.canCreate && (
            <Button type="button" disabled={mutationPending} onClick={handleCreate}>
              {createMutation.isPending ? "Creating…" : "Create postmortem"}
            </Button>
          )}
        </div>
      )}

      {viewState.kind === "content" && postmortem && (
        <>
          <div className="flex flex-wrap gap-2">
            {actions.showEdit && !showDraftEditor && (
              <Button type="button" variant="secondary" disabled={mutationPending} onClick={startEditing}>
                Edit
              </Button>
            )}
            {actions.showPublish && !showDraftEditor && (
              <Button type="button" disabled={mutationPending} onClick={handlePublish}>
                {publishMutation.isPending ? "Publishing…" : "Publish"}
              </Button>
            )}
            {actions.showUnpublish && (
              <Button
                type="button"
                variant="outline"
                disabled={mutationPending}
                onClick={handleUnpublish}
              >
                {unpublishMutation.isPending ? "Unpublishing…" : "Unpublish"}
              </Button>
            )}
            {actions.showArchive && (
              <Button type="button" variant="outline" disabled={mutationPending} onClick={handleArchive}>
                {archiveMutation.isPending ? "Archiving…" : "Archive"}
              </Button>
            )}
            {actions.showUnarchive && (
              <Button type="button" variant="outline" disabled={mutationPending} onClick={handleUnarchive}>
                {unarchiveMutation.isPending ? "Unarchiving…" : "Unarchive"}
              </Button>
            )}
            {actions.showDelete && (
              <Button
                type="button"
                variant="outline"
                disabled={mutationPending}
                onClick={handleDelete}
              >
                {deleteMutation.isPending ? "Deleting…" : "Delete draft"}
              </Button>
            )}
          </div>

          <FieldError message={actionError ?? undefined} />

          {showDraftEditor && draft ? (
            <IncidentPostmortemEditor
              values={draft}
              onChange={setDraft}
              onSave={handleSave}
              onCancel={cancelEditing}
              isSaving={updateMutation.isPending}
              formError={formError}
            />
          ) : (
            <IncidentPostmortemDisplay postmortem={postmortem} />
          )}

          {postmortem.status === "DRAFT" && actions.showPublish && showDraftEditor && (
            <div className="space-y-2 border-t pt-4">
              <p className="text-sm text-muted-foreground">
                Summary and root cause must be filled in before publishing.
              </p>
              <Button type="button" disabled={mutationPending} onClick={handlePublish}>
                {publishMutation.isPending ? "Publishing…" : "Publish"}
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
