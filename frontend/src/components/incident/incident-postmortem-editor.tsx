"use client";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  POSTMORTEM_SECTION_MAX_LENGTH,
  POSTMORTEM_TITLE_MAX_LENGTH,
} from "@/lib/postmortem-rbac";
import type { PostmortemFormValues } from "@/lib/postmortem-form";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

const SECTION_FIELDS: {
  key: keyof Omit<PostmortemFormValues, "title">;
  label: string;
  placeholder: string;
}[] = [
  {
    key: "summary",
    label: "Summary",
    placeholder: "What happened and what was the customer or business impact?",
  },
  {
    key: "impact",
    label: "Impact",
    placeholder: "Who was affected and for how long?",
  },
  {
    key: "rootCause",
    label: "Root cause",
    placeholder: "Why did this incident occur?",
  },
  {
    key: "resolution",
    label: "Resolution",
    placeholder: "How was the incident mitigated and resolved?",
  },
  {
    key: "lessonsLearned",
    label: "Lessons learned",
    placeholder: "What should the team remember for next time?",
  },
  {
    key: "correctiveActions",
    label: "Corrective actions",
    placeholder: "Follow-up work to prevent recurrence.",
  },
];

type IncidentPostmortemEditorProps = {
  values: PostmortemFormValues;
  onChange: (values: PostmortemFormValues) => void;
  onSave: () => void;
  onCancel: () => void;
  isSaving: boolean;
  formError?: string | null;
  idPrefix?: string;
};

export function IncidentPostmortemEditor({
  values,
  onChange,
  onSave,
  onCancel,
  isSaving,
  formError,
  idPrefix = "postmortem",
}: IncidentPostmortemEditorProps) {
  function setField<K extends keyof PostmortemFormValues>(key: K, value: PostmortemFormValues[K]) {
    onChange({ ...values, [key]: value });
  }

  return (
    <form
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-title`}>Title</Label>
        <Input
          id={`${idPrefix}-title`}
          value={values.title}
          maxLength={POSTMORTEM_TITLE_MAX_LENGTH}
          disabled={isSaving}
          onChange={(event) => setField("title", event.target.value)}
        />
        <p className="text-xs text-muted-foreground" aria-live="polite">
          {values.title.length} / {POSTMORTEM_TITLE_MAX_LENGTH}
        </p>
      </div>

      {SECTION_FIELDS.map(({ key, label, placeholder }) => (
        <div key={key} className="space-y-2">
          <Label htmlFor={`${idPrefix}-${key}`}>{label}</Label>
          <textarea
            id={`${idPrefix}-${key}`}
            className={textareaClassName}
            value={values[key]}
            placeholder={placeholder}
            maxLength={POSTMORTEM_SECTION_MAX_LENGTH}
            disabled={isSaving}
            onChange={(event) => setField(key, event.target.value)}
          />
          <p className="text-xs text-muted-foreground" aria-live="polite">
            {values[key].length} / {POSTMORTEM_SECTION_MAX_LENGTH}
          </p>
        </div>
      ))}

      <FieldError message={formError ?? undefined} />

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={isSaving}>
          {isSaving ? "Saving…" : "Save changes"}
        </Button>
        <Button type="button" variant="outline" disabled={isSaving} onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
