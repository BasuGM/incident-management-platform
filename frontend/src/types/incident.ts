export type IncidentStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "CANCELLED";

export type IncidentSeverity = "SEV1" | "SEV2" | "SEV3" | "SEV4";

export type Incident = {
  id: string;
  organizationId: string;
  incidentNumber: number;
  displayId: string;
  title: string;
  description: string | null;
  severity: IncidentSeverity;
  status: IncidentStatus;
  serviceId: string | null;
  serviceName: string | null;
  reporterId: string;
  reporterEmail: string;
  reporterFirstName: string;
  reporterLastName: string;
  commanderId: string | null;
  commanderEmail: string | null;
  commanderFirstName: string | null;
  commanderLastName: string | null;
  createdAt: string;
  updatedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  cancelledAt: string | null;
};

export type IncidentPage = {
  content: Incident[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateIncidentInput = {
  title: string;
  description?: string;
  severity: IncidentSeverity;
  serviceId?: string;
  commanderId?: string;
};

/** PATCH body: only include keys that are being sent. Use null to clear nullable fields. */
export type UpdateIncidentBody = {
  title?: string;
  description?: string | null;
  severity?: IncidentSeverity;
  serviceId?: string | null;
  commanderId?: string | null;
  status?: IncidentStatus;
};

export type IncidentEventType =
  | "INCIDENT_CREATED"
  | "STATUS_CHANGED"
  | "SEVERITY_CHANGED"
  | "SERVICE_CHANGED"
  | "COMMANDER_CHANGED"
  | "TITLE_CHANGED"
  | "DESCRIPTION_CHANGED";

export type IncidentEventPayload = Record<string, unknown>;

export type IncidentEvent = {
  id: string;
  incidentId: string;
  organizationId: string;
  actorId: string;
  actorEmail: string;
  actorFirstName: string;
  actorLastName: string;
  type: IncidentEventType;
  payload: IncidentEventPayload;
  createdAt: string;
};

export type IncidentEventPage = {
  content: IncidentEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type IncidentComment = {
  id: string;
  organizationId: string;
  incidentId: string;
  authorId: string;
  authorEmail: string;
  authorFirstName: string;
  authorLastName: string;
  body: string;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
};

export type IncidentCommentPage = {
  content: IncidentComment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateIncidentCommentInput = {
  body: string;
};

export type UpdateIncidentCommentInput = {
  body: string;
};

export type IncidentPostmortemStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type OrganizationPostmortemListStatus = "PUBLISHED" | "DRAFT" | "ARCHIVED" | "ALL";

export type IncidentPostmortem = {
  id: string;
  organizationId: string;
  incidentId: string;
  authorId: string;
  authorEmail: string;
  authorFirstName: string;
  authorLastName: string;
  status: IncidentPostmortemStatus;
  title: string;
  summary: string;
  impact: string;
  rootCause: string;
  resolution: string;
  lessonsLearned: string;
  correctiveActions: string;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  publishedById: string | null;
  publishedByEmail: string | null;
  publishedByFirstName: string | null;
  publishedByLastName: string | null;
  archivedAt: string | null;
};

export type IncidentPostmortemPage = {
  content: IncidentPostmortem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/** Optional fields on create; omitted keys use server defaults. */
export type CreateIncidentPostmortemInput = {
  title?: string;
  summary?: string;
  impact?: string;
  rootCause?: string;
  resolution?: string;
  lessonsLearned?: string;
  correctiveActions?: string;
};

/**
 * PATCH body: include only keys being updated. Explicit `null` clears a text section (stored as empty).
 * Omitted keys are left unchanged on the server.
 */
export type UpdateIncidentPostmortemBody = {
  title?: string;
  summary?: string | null;
  impact?: string | null;
  rootCause?: string | null;
  resolution?: string | null;
  lessonsLearned?: string | null;
  correctiveActions?: string | null;
};
