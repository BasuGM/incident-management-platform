"use client";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Label } from "@/components/ui/label";
import {
  canDeleteComment,
  canEditComment,
  COMMENT_MAX_BODY_LENGTH,
} from "@/lib/comment-rbac";
import { memberDisplayName } from "@/lib/incident-display";
import {
  formatEventAbsoluteTime,
  formatEventRelativeTime,
} from "@/lib/incident-event-display";
import type { IncidentComment } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";
import { useEffect, useRef, useState } from "react";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

type IncidentCommentItemProps = {
  comment: IncidentComment;
  organizationRole: OrganizationRole;
  currentUserId: string;
  incidentWritable: boolean;
  isUpdating: boolean;
  isDeleting: boolean;
  mutationError?: string | null;
  onSave: (commentId: string, body: string) => void;
  onDelete: (commentId: string) => void;
};

export function IncidentCommentItem({
  comment,
  organizationRole,
  currentUserId,
  incidentWritable,
  isUpdating,
  isDeleting,
  mutationError,
  onSave,
  onDelete,
}: IncidentCommentItemProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editBody, setEditBody] = useState(comment.body);
  const [localError, setLocalError] = useState<string | null>(null);

  const authorName = memberDisplayName(
    comment.authorFirstName,
    comment.authorLastName,
    comment.authorEmail,
  );
  const timestamp = comment.deleted && comment.deletedAt ? comment.deletedAt : comment.createdAt;
  const showEdit = canEditComment(organizationRole, comment, currentUserId, incidentWritable);
  const showDelete = canDeleteComment(organizationRole, comment, currentUserId, incidentWritable);

  const wasUpdating = useRef(false);

  useEffect(() => {
    if (wasUpdating.current && !isUpdating) {
      setIsEditing(false);
      setLocalError(null);
    }
    wasUpdating.current = isUpdating;
  }, [isUpdating]);

  function cancelEdit() {
    setEditBody(comment.body);
    setLocalError(null);
    setIsEditing(false);
  }

  if (comment.deleted) {
    return (
      <li className="rounded-lg border border-dashed bg-muted/30 px-4 py-3">
        <p className="text-sm font-medium text-muted-foreground">Comment deleted</p>
        <p className="mt-1 text-xs text-muted-foreground">
          <span>{authorName}</span>
          <span className="mx-1" aria-hidden="true">·</span>
          <time dateTime={timestamp} title={formatEventAbsoluteTime(timestamp)}>
            {formatEventRelativeTime(timestamp)}
          </time>
        </p>
      </li>
    );
  }

  return (
    <li className="rounded-lg border px-4 py-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium">{authorName}</p>
          <p className="text-xs text-muted-foreground">{comment.authorEmail}</p>
        </div>
        {(showEdit || showDelete) && !isEditing && (
          <div className="flex shrink-0 gap-2">
            {showEdit && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isUpdating || isDeleting}
                onClick={() => {
                  setEditBody(comment.body);
                  setLocalError(null);
                  setIsEditing(true);
                }}
              >
                Edit
              </Button>
            )}
            {showDelete && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isUpdating || isDeleting}
                onClick={() => {
                  if (!window.confirm("Delete this comment? This cannot be undone.")) {
                    return;
                  }
                  onDelete(comment.id);
                }}
              >
                Delete
              </Button>
            )}
          </div>
        )}
      </div>

      {isEditing ? (
        <form
          className="mt-3 space-y-2"
          onSubmit={(event) => {
            event.preventDefault();
            const trimmed = editBody.trim();
            if (!trimmed) {
              setLocalError("Comment cannot be empty.");
              return;
            }
            if (trimmed.length > COMMENT_MAX_BODY_LENGTH) {
              setLocalError(`Comment must be at most ${COMMENT_MAX_BODY_LENGTH} characters.`);
              return;
            }
            setLocalError(null);
            onSave(comment.id, trimmed);
          }}
        >
          <Label htmlFor={`edit-comment-${comment.id}`}>Edit comment</Label>
          <textarea
            id={`edit-comment-${comment.id}`}
            className={textareaClassName}
            value={editBody}
            maxLength={COMMENT_MAX_BODY_LENGTH}
            disabled={isUpdating}
            onChange={(event) => setEditBody(event.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            {editBody.length} / {COMMENT_MAX_BODY_LENGTH}
          </p>
          <FieldError message={localError ?? mutationError ?? undefined} />
          <div className="flex gap-2">
            <Button type="submit" size="sm" disabled={isUpdating || !editBody.trim()}>
              {isUpdating ? "Saving…" : "Save"}
            </Button>
            <Button type="button" variant="outline" size="sm" disabled={isUpdating} onClick={cancelEdit}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <>
          <p className="mt-2 whitespace-pre-wrap break-words text-sm">{comment.body}</p>
          <p className="mt-2 text-xs text-muted-foreground">
            <time dateTime={comment.createdAt} title={formatEventAbsoluteTime(comment.createdAt)}>
              {formatEventRelativeTime(comment.createdAt)}
            </time>
            <span className="mx-1" aria-hidden="true">·</span>
            <span>{formatEventAbsoluteTime(comment.createdAt)}</span>
          </p>
          {mutationError && !isEditing && <FieldError message={mutationError} />}
        </>
      )}
    </li>
  );
}
