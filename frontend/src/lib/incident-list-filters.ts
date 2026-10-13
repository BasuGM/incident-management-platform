import type { IncidentSeverity, IncidentStatus } from "@/types/incident";

export const INCIDENT_LIST_PAGE_SIZE = 20;
export const MAX_INCIDENT_SEARCH_LENGTH = 200;

const DISCOVERY_PARAM_KEYS = [
  "active",
  "status",
  "severity",
  "serviceId",
  "commanderId",
  "mine",
  "search",
] as const;

const INCIDENT_STATUSES: IncidentStatus[] = ["OPEN", "ACKNOWLEDGED", "RESOLVED", "CANCELLED"];
const INCIDENT_SEVERITIES: IncidentSeverity[] = ["SEV1", "SEV2", "SEV3", "SEV4"];

export type IncidentListFilters = {
  page: number;
  size: number;
  /** When true, list all statuses (`active=all` in URL; omitted from API). */
  allStatuses?: boolean;
  active?: boolean;
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  serviceId?: string;
  commanderId?: string;
  mine?: boolean;
  search?: string;
};

export type IncidentListApiQuery = {
  page: number;
  size: number;
  active?: boolean;
  status?: IncidentStatus;
  severity?: IncidentSeverity;
  serviceId?: string;
  commanderId?: string;
  mine?: boolean;
  search?: string;
};

export function defaultIncidentListFilters(): IncidentListFilters {
  return {
    page: 0,
    size: INCIDENT_LIST_PAGE_SIZE,
    active: true,
  };
}

export function hasDiscoveryParams(searchParams: URLSearchParams): boolean {
  return DISCOVERY_PARAM_KEYS.some((key) => searchParams.has(key));
}

/** True when the URL should be upgraded to the default active-incidents view. */
export function shouldApplyDefaultActiveView(searchParams: URLSearchParams): boolean {
  return !hasDiscoveryParams(searchParams);
}

function parseBooleanParam(value: string | null): boolean | undefined {
  if (value === null || value === "") {
    return undefined;
  }
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  return undefined;
}

function parseStatusParam(value: string | null): IncidentStatus | undefined {
  if (!value) {
    return undefined;
  }
  const upper = value.toUpperCase();
  return INCIDENT_STATUSES.includes(upper as IncidentStatus) ? (upper as IncidentStatus) : undefined;
}

function parseSeverityParam(value: string | null): IncidentSeverity | undefined {
  if (!value) {
    return undefined;
  }
  const upper = value.toUpperCase();
  return INCIDENT_SEVERITIES.includes(upper as IncidentSeverity)
    ? (upper as IncidentSeverity)
    : undefined;
}

function parsePageParam(value: string | null): number {
  if (!value) {
    return 0;
  }
  const parsed = Number.parseInt(value, 10);
  if (!Number.isFinite(parsed) || parsed < 0) {
    return 0;
  }
  return parsed;
}

function parseTrimmedParam(value: string | null): string | undefined {
  if (value === null) {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

/**
 * Parse URL search params into filter state. When no discovery params exist, returns the default active view.
 */
export function parseIncidentListFilters(searchParams: URLSearchParams): IncidentListFilters {
  if (shouldApplyDefaultActiveView(searchParams)) {
    return defaultIncidentListFilters();
  }

  const page = parsePageParam(searchParams.get("page"));
  const status = parseStatusParam(searchParams.get("status"));
  const activeRaw = searchParams.get("active");
  const allStatuses = !status && activeRaw === "all";
  const active =
      status || allStatuses ? undefined : parseBooleanParam(activeRaw);

  const mine = parseBooleanParam(searchParams.get("mine"));
  let commanderId = parseTrimmedParam(searchParams.get("commanderId"));

  if (mine === true && commanderId) {
    commanderId = undefined;
  }

  const search = parseTrimmedParam(searchParams.get("search"));
  const boundedSearch =
    search && search.length > MAX_INCIDENT_SEARCH_LENGTH
      ? search.slice(0, MAX_INCIDENT_SEARCH_LENGTH)
      : search;

  return normalizeIncidentListFilters({
    page,
    size: INCIDENT_LIST_PAGE_SIZE,
    allStatuses: allStatuses || undefined,
    active,
    status,
    severity: parseSeverityParam(searchParams.get("severity")),
    serviceId: parseTrimmedParam(searchParams.get("serviceId")),
    commanderId,
    mine: mine === true ? true : undefined,
    search: boundedSearch,
  });
}

export function normalizeIncidentListFilters(filters: IncidentListFilters): IncidentListFilters {
  const next: IncidentListFilters = {
    page: Math.max(0, filters.page),
    size: filters.size,
  };

  if (filters.status) {
    next.status = filters.status;
  } else if (filters.allStatuses) {
    next.allStatuses = true;
  } else if (filters.active !== undefined) {
    next.active = filters.active;
  }

  if (filters.severity) {
    next.severity = filters.severity;
  }
  if (filters.serviceId) {
    next.serviceId = filters.serviceId;
  }

  if (filters.mine === true) {
    next.mine = true;
  } else if (filters.commanderId) {
    next.commanderId = filters.commanderId;
  }

  if (filters.search) {
    const trimmed = filters.search.trim();
    if (trimmed.length > 0) {
      next.search = trimmed;
    }
  }

  return next;
}

export function serializeIncidentListSearchParams(filters: IncidentListFilters): string {
  const params = new URLSearchParams();
  const normalized = normalizeIncidentListFilters(filters);

  if (normalized.status) {
    params.set("status", normalized.status);
  } else if (normalized.allStatuses) {
    params.set("active", "all");
  } else if (normalized.active === true) {
    params.set("active", "true");
  } else if (normalized.active === false) {
    params.set("active", "false");
  }

  if (normalized.severity) {
    params.set("severity", normalized.severity);
  }
  if (normalized.serviceId) {
    params.set("serviceId", normalized.serviceId);
  }
  if (normalized.mine === true) {
    params.set("mine", "true");
  } else if (normalized.commanderId) {
    params.set("commanderId", normalized.commanderId);
  }
  if (normalized.search) {
    params.set("search", normalized.search);
  }
  if (normalized.page > 0) {
    params.set("page", String(normalized.page));
  }

  return params.toString();
}

export function incidentListFiltersToApiQuery(filters: IncidentListFilters): IncidentListApiQuery {
  const normalized = normalizeIncidentListFilters(filters);
  const query: IncidentListApiQuery = {
    page: normalized.page,
    size: normalized.size,
  };

  if (normalized.status) {
    query.status = normalized.status;
  } else if (normalized.active !== undefined) {
    query.active = normalized.active;
  }

  if (normalized.severity) {
    query.severity = normalized.severity;
  }
  if (normalized.serviceId) {
    query.serviceId = normalized.serviceId;
  }
  if (normalized.mine === true) {
    query.mine = true;
  } else if (normalized.commanderId) {
    query.commanderId = normalized.commanderId === "me" ? "me" : normalized.commanderId;
  }
  if (normalized.search) {
    query.search = normalized.search;
  }

  return query;
}

export function buildIncidentListQueryString(query: IncidentListApiQuery): string {
  const params = new URLSearchParams();
  params.set("page", String(query.page));
  params.set("size", String(query.size));

  if (query.status) {
    params.set("status", query.status);
  } else if (query.active !== undefined) {
    params.set("active", String(query.active));
  }

  if (query.severity) {
    params.set("severity", query.severity);
  }
  if (query.serviceId) {
    params.set("serviceId", query.serviceId);
  }
  if (query.mine === true) {
    params.set("mine", "true");
  } else if (query.commanderId) {
    params.set("commanderId", query.commanderId);
  }
  if (query.search) {
    params.set("search", query.search);
  }

  return params.toString();
}

export function incidentListFiltersQueryKeyPayload(filters: IncidentListFilters): string {
  return serializeIncidentListSearchParams(normalizeIncidentListFilters(filters));
}

export function isDefaultActiveIncidentListView(filters: IncidentListFilters): boolean {
  const normalized = normalizeIncidentListFilters(filters);
  return (
    normalized.page === 0 &&
    normalized.active === true &&
    !normalized.allStatuses &&
    !normalized.status &&
    !normalized.severity &&
    !normalized.serviceId &&
    !normalized.mine &&
    !normalized.commanderId &&
    !normalized.search
  );
}

export function hasAppliedIncidentListFilters(filters: IncidentListFilters): boolean {
  return !isDefaultActiveIncidentListView(filters);
}

export function clearIncidentListFilters(): IncidentListFilters {
  return defaultIncidentListFilters();
}

export function withIncidentListPage(filters: IncidentListFilters, page: number): IncidentListFilters {
  return normalizeIncidentListFilters({ ...filters, page: Math.max(0, page) });
}

export function withIncidentListFilterChange(
  filters: IncidentListFilters,
  patch: Partial<Omit<IncidentListFilters, "page" | "size">>,
): IncidentListFilters {
  return normalizeIncidentListFilters({
    ...filters,
    ...patch,
    page: 0,
  });
}

export function clampIncidentListPage(page: number, totalPages: number): number {
  if (totalPages <= 0) {
    return 0;
  }
  return Math.min(Math.max(0, page), totalPages - 1);
}

export function incidentListPath(organizationId: string, filters: IncidentListFilters): string {
  const qs = serializeIncidentListSearchParams(filters);
  return qs
    ? `/organizations/${organizationId}/incidents?${qs}`
    : `/organizations/${organizationId}/incidents`;
}
