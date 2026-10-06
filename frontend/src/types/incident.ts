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
