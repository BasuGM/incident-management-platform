import {
  POSTMORTEM_SECTION_MAX_LENGTH,
  POSTMORTEM_TITLE_MAX_LENGTH,
} from "@/lib/postmortem-rbac";
import type { IncidentPostmortem, UpdateIncidentPostmortemBody } from "@/types/incident";

export type PostmortemFormValues = {
  title: string;
  summary: string;
  impact: string;
  rootCause: string;
  resolution: string;
  lessonsLearned: string;
  correctiveActions: string;
};

const SECTION_KEYS: (keyof Omit<PostmortemFormValues, "title">)[] = [
  "summary",
  "impact",
  "rootCause",
  "resolution",
  "lessonsLearned",
  "correctiveActions",
];

export function postmortemToFormValues(postmortem: IncidentPostmortem): PostmortemFormValues {
  return {
    title: postmortem.title,
    summary: postmortem.summary,
    impact: postmortem.impact,
    rootCause: postmortem.rootCause,
    resolution: postmortem.resolution,
    lessonsLearned: postmortem.lessonsLearned,
    correctiveActions: postmortem.correctiveActions,
  };
}

export function emptyPostmortemFormValues(): PostmortemFormValues {
  return {
    title: "",
    summary: "",
    impact: "",
    rootCause: "",
    resolution: "",
    lessonsLearned: "",
    correctiveActions: "",
  };
}

export function validatePostmortemForm(values: PostmortemFormValues): string | null {
  if (values.title.length > POSTMORTEM_TITLE_MAX_LENGTH) {
    return `Title must be at most ${POSTMORTEM_TITLE_MAX_LENGTH} characters.`;
  }
  for (const key of SECTION_KEYS) {
    if (values[key].length > POSTMORTEM_SECTION_MAX_LENGTH) {
      return `Each section must be at most ${POSTMORTEM_SECTION_MAX_LENGTH} characters.`;
    }
  }
  return null;
}

export function getPublishValidationError(values: Pick<PostmortemFormValues, "summary" | "rootCause">) {
  if (!values.summary.trim()) {
    return "Summary is required to publish.";
  }
  if (!values.rootCause.trim()) {
    return "Root cause is required to publish.";
  }
  return null;
}

export function buildPostmortemUpdateBody(
  current: IncidentPostmortem,
  draft: PostmortemFormValues,
): UpdateIncidentPostmortemBody {
  const body: UpdateIncidentPostmortemBody = {};
  const trimmedTitle = draft.title.trim();
  if (trimmedTitle !== current.title) {
    body.title = trimmedTitle;
  }

  for (const key of SECTION_KEYS) {
    const trimmed = draft[key].trim();
    const previous = current[key];
    if (trimmed !== previous) {
      body[key] = trimmed === "" ? null : trimmed;
    }
  }

  return body;
}

export function isPostmortemMutationPending(flags: {
  create?: boolean;
  update?: boolean;
  publish?: boolean;
  unpublish?: boolean;
  archive?: boolean;
  unarchive?: boolean;
  delete?: boolean;
}) {
  return Object.values(flags).some(Boolean);
}
