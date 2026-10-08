"use client";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Label } from "@/components/ui/label";
import { COMMENT_MAX_BODY_LENGTH } from "@/lib/comment-rbac";
import { useState } from "react";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

type IncidentCommentComposerProps = {
  disabled?: boolean;
  isSubmitting: boolean;
  errorMessage?: string | null;
  onSubmit: (body: string) => void;
};

export function IncidentCommentComposer({
  disabled = false,
  isSubmitting,
  errorMessage,
  onSubmit,
}: IncidentCommentComposerProps) {
  const [body, setBody] = useState("");

  const trimmed = body.trim();
  const canSubmit =
    !disabled && !isSubmitting && trimmed.length > 0 && trimmed.length <= COMMENT_MAX_BODY_LENGTH;

  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!canSubmit) {
          return;
        }
        onSubmit(trimmed);
        setBody("");
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="incident-comment-body">Add a comment</Label>
        <textarea
          id="incident-comment-body"
          className={textareaClassName}
          placeholder="Share an update or question about this incident…"
          value={body}
          maxLength={COMMENT_MAX_BODY_LENGTH}
          disabled={disabled || isSubmitting}
          onChange={(event) => setBody(event.target.value)}
        />
        <p className="text-xs text-muted-foreground" aria-live="polite">
          {body.length} / {COMMENT_MAX_BODY_LENGTH}
        </p>
      </div>
      <FieldError message={errorMessage ?? undefined} />
      <Button type="submit" disabled={!canSubmit}>
        {isSubmitting ? "Posting…" : "Post comment"}
      </Button>
    </form>
  );
}
