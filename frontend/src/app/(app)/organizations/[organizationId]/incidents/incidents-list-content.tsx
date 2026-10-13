"use client";

import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from "@/components/incident/incident-badges";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/components/providers/auth-provider";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { useOrganizationIncidentsQuery } from "@/lib/hooks/use-organization-incidents";
import {
  clampIncidentListPage,
  clearIncidentListFilters,
  hasAppliedIncidentListFilters,
  incidentListPath,
  isDefaultActiveIncidentListView,
  MAX_INCIDENT_SEARCH_LENGTH,
  parseIncidentListFilters,
  shouldApplyDefaultActiveView,
  withIncidentListFilterChange,
  withIncidentListPage,
  type IncidentListFilters,
} from "@/lib/incident-list-filters";
import {
  commanderDisplayName,
  formatIncidentTimestamp,
  memberDisplayName,
  reporterDisplayName,
} from "@/lib/incident-display";
import { canCreateIncident, canManageOrganization } from "@/lib/organization-rbac";
import type { IncidentSeverity, IncidentStatus } from "@/types/incident";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const SEARCH_DEBOUNCE_MS = 300;

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "Any (use view preset)" },
  { value: "OPEN", label: "Open" },
  { value: "ACKNOWLEDGED", label: "Acknowledged" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "CANCELLED", label: "Cancelled" },
];

const SEVERITY_OPTIONS: IncidentSeverity[] = ["SEV1", "SEV2", "SEV3", "SEV4"];

const VIEW_PRESET_OPTIONS = [
  { value: "active", label: "Active incidents" },
  { value: "all", label: "All incidents" },
  { value: "closed", label: "Resolved & cancelled" },
] as const;

type ViewPreset = (typeof VIEW_PRESET_OPTIONS)[number]["value"];

function viewPresetFromFilters(filters: IncidentListFilters): ViewPreset {
  if (filters.status) {
    return "all";
  }
  if (filters.allStatuses) {
    return "all";
  }
  if (filters.active === false) {
    return "closed";
  }
  return "active";
}

function filtersFromViewPreset(preset: ViewPreset, current: IncidentListFilters): IncidentListFilters {
  if (preset === "active") {
    return withIncidentListFilterChange(current, {
      allStatuses: undefined,
      active: true,
      status: undefined,
    });
  }
  if (preset === "closed") {
    return withIncidentListFilterChange(current, {
      allStatuses: undefined,
      active: false,
      status: undefined,
    });
  }
  return withIncidentListFilterChange(current, {
    allStatuses: true,
    active: undefined,
    status: undefined,
  });
}

export function IncidentsListContent({ organizationId }: { organizationId: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user } = useAuth();

  const filters = useMemo(() => parseIncidentListFilters(searchParams), [searchParams]);

  const [searchDraft, setSearchDraft] = useState(filters.search ?? "");
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep the search field aligned when the URL changes (back/forward, clear filters).
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- sync controlled input to URL-derived filters
    setSearchDraft(filters.search ?? "");
  }, [filters.search]);

  useEffect(() => {
    if (!shouldApplyDefaultActiveView(searchParams)) {
      return;
    }
    router.replace(incidentListPath(organizationId, clearIncidentListFilters()));
  }, [organizationId, router, searchParams]);

  const navigateFilters = useCallback(
    (next: IncidentListFilters) => {
      router.push(incidentListPath(organizationId, next));
    },
    [organizationId, router],
  );

  const applySearchToUrl = useCallback(
    (value: string) => {
      const trimmed = value.trim();
      const bounded =
        trimmed.length > MAX_INCIDENT_SEARCH_LENGTH
          ? trimmed.slice(0, MAX_INCIDENT_SEARCH_LENGTH)
          : trimmed;
      navigateFilters(
        withIncidentListFilterChange(filters, {
          search: bounded.length > 0 ? bounded : undefined,
        }),
      );
    },
    [filters, navigateFilters],
  );

  const handleSearchChange = (value: string) => {
    setSearchDraft(value);
    if (searchDebounceRef.current) {
      clearTimeout(searchDebounceRef.current);
    }
    searchDebounceRef.current = setTimeout(() => {
      applySearchToUrl(value);
    }, SEARCH_DEBOUNCE_MS);
  };

  const handleSearchKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      if (searchDebounceRef.current) {
        clearTimeout(searchDebounceRef.current);
      }
      applySearchToUrl(searchDraft);
    }
  };

  useEffect(() => {
    return () => {
      if (searchDebounceRef.current) {
        clearTimeout(searchDebounceRef.current);
      }
    };
  }, []);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
    enabled: Boolean(user?.id && organizationQuery.data),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
    enabled: Boolean(user?.id && organizationQuery.data),
  });

  const incidentsQuery = useOrganizationIncidentsQuery({
    organizationId,
    filters,
    enabled: Boolean(organizationQuery.data && user?.id),
  });

  const incidentPage = incidentsQuery.data;

  useEffect(() => {
    if (!incidentPage) {
      return;
    }
    const clamped = clampIncidentListPage(filters.page, incidentPage.totalPages);
    if (clamped !== filters.page) {
      navigateFilters(withIncidentListPage(filters, clamped));
    }
  }, [filters, incidentPage, navigateFilters]);

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;
  const canCreate = organization ? canCreateIncident(organization.currentUserRole) : false;

  const filtersApplied = hasAppliedIncidentListFilters(filters);
  const isDefaultActive = isDefaultActiveIncidentListView(filters);
  const isFilteredEmpty = incidentPage && incidentPage.totalElements === 0 && filtersApplied;
  const isOrgEmpty =
    incidentPage && incidentPage.totalElements === 0 && !filtersApplied && isDefaultActive;

  const commanderSelectValue = filters.mine ? "me" : filters.commanderId ?? "";

  const subtitle = isDefaultActive
    ? "Showing open and acknowledged incidents."
    : filters.allStatuses
      ? "Showing all incident statuses."
      : filters.active === false
        ? "Showing resolved and cancelled incidents."
        : filters.status
          ? `Filtered by status: ${filters.status}.`
          : "Showing open and acknowledged incidents.";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link
            href={`/organizations/${organizationId}`}
            className="text-sm text-primary hover:underline"
          >
            ← Back to organization
          </Link>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">Incidents</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {organization ? `Issues for ${organization.name}` : "Organization incidents"}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
          {filtersApplied && (
            <p className="mt-1 text-xs font-medium text-foreground">Filters applied</p>
          )}
        </div>
        {canCreate && (
          <Link
            href={`/organizations/${organizationId}/incidents/new`}
            className={buttonVariants({ variant: "default" })}
          >
            Create incident
          </Link>
        )}
      </div>

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

      <section className="space-y-4 rounded-lg border p-4" aria-label="Incident filters">
        <div className="flex flex-wrap items-end gap-4">
          <div className="space-y-2">
            <Label htmlFor="incident-view-preset">View</Label>
            <select
              id="incident-view-preset"
              className="flex h-10 min-w-[12rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={viewPresetFromFilters(filters)}
              onChange={(event) => {
                navigateFilters(filtersFromViewPreset(event.target.value as ViewPreset, filters));
              }}
            >
              {VIEW_PRESET_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="incident-status-filter">Status</Label>
            <select
              id="incident-status-filter"
              className="flex h-10 min-w-[10rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={filters.status ?? ""}
              onChange={(event) => {
                const value = event.target.value as IncidentStatus | "";
                navigateFilters(
                  withIncidentListFilterChange(filters, {
                    status: value || undefined,
                    active: value ? undefined : filters.active ?? true,
                  }),
                );
              }}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option.label} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="incident-severity-filter">Severity</Label>
            <select
              id="incident-severity-filter"
              className="flex h-10 min-w-[8rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={filters.severity ?? ""}
              onChange={(event) => {
                const value = event.target.value as IncidentSeverity | "";
                navigateFilters(
                  withIncidentListFilterChange(filters, {
                    severity: value || undefined,
                  }),
                );
              }}
            >
              <option value="">Any</option>
              {SEVERITY_OPTIONS.map((severity) => (
                <option key={severity} value={severity}>
                  {severity}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="incident-service-filter">Service</Label>
            <select
              id="incident-service-filter"
              className="flex h-10 min-w-[10rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={filters.serviceId ?? ""}
              onChange={(event) => {
                navigateFilters(
                  withIncidentListFilterChange(filters, {
                    serviceId: event.target.value || undefined,
                  }),
                );
              }}
            >
              <option value="">Any service</option>
              {(servicesQuery.data ?? []).map((service) => (
                <option key={service.id} value={service.id}>
                  {service.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="incident-commander-filter">Commander</Label>
            <select
              id="incident-commander-filter"
              className="flex h-10 min-w-[12rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={commanderSelectValue}
              onChange={(event) => {
                const value = event.target.value;
                if (value === "me") {
                  navigateFilters(
                    withIncidentListFilterChange(filters, {
                      mine: true,
                      commanderId: undefined,
                    }),
                  );
                  return;
                }
                navigateFilters(
                  withIncidentListFilterChange(filters, {
                    mine: undefined,
                    commanderId: value || undefined,
                  }),
                );
              }}
            >
              <option value="">Anyone</option>
              <option value="me">My incidents</option>
              {(membersQuery.data ?? []).map((member) => (
                <option key={member.userId} value={member.userId}>
                  {memberDisplayName(member.firstName, member.lastName, member.email)}
                </option>
              ))}
            </select>
          </div>

          <div className="min-w-[12rem] flex-1 space-y-2">
            <Label htmlFor="incident-search">Search</Label>
            <Input
              id="incident-search"
              type="search"
              placeholder="Title or INC- number"
              value={searchDraft}
              maxLength={MAX_INCIDENT_SEARCH_LENGTH}
              onChange={(event) => handleSearchChange(event.target.value)}
              onKeyDown={handleSearchKeyDown}
            />
          </div>

          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mb-0.5"
            disabled={!filtersApplied}
            onClick={() => navigateFilters(clearIncidentListFilters())}
          >
            Clear filters
          </Button>
        </div>
      </section>

      {incidentsQuery.isPending && (
        <p className="text-sm text-muted-foreground" role="status">Loading incidents…</p>
      )}
      {incidentsQuery.isError && (
        <div className="space-y-2">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(incidentsQuery.error, "Failed to load incidents")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void incidentsQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {isOrgEmpty && (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <h2 className="text-lg font-medium">No incidents yet</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Create the first incident for this organization to start tracking production issues.
          </p>
          {canCreate && (
            <Link
              href={`/organizations/${organizationId}/incidents/new`}
              className={`${buttonVariants({ variant: "default" })} mt-4 inline-flex`}
            >
              Create incident
            </Link>
          )}
        </div>
      )}

      {isFilteredEmpty && (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <h2 className="text-lg font-medium">No matching incidents</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            No incidents match the current filters. Try adjusting your search or clear filters to
            return to the active incidents view.
          </p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={() => navigateFilters(clearIncidentListFilters())}
          >
            Clear filters
          </Button>
        </div>
      )}

      {incidentPage && incidentPage.totalElements > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="border-b bg-muted/40 text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">ID</th>
                  <th className="px-3 py-2 font-medium">Title</th>
                  <th className="px-3 py-2 font-medium">Severity</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Service</th>
                  <th className="px-3 py-2 font-medium">Commander</th>
                  <th className="px-3 py-2 font-medium">Reporter</th>
                  <th className="px-3 py-2 font-medium">Created</th>
                  <th className="px-3 py-2 font-medium" />
                </tr>
              </thead>
              <tbody>
                {incidentPage.content.map((incident) => (
                  <tr key={incident.id} className="border-b last:border-b-0">
                    <td className="px-3 py-2 font-mono text-xs">{incident.displayId}</td>
                    <td className="px-3 py-2">{incident.title}</td>
                    <td className="px-3 py-2">
                      <IncidentSeverityBadge severity={incident.severity} />
                    </td>
                    <td className="px-3 py-2">
                      <IncidentStatusBadge status={incident.status} />
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      {incident.serviceName ?? "—"}
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      {commanderDisplayName(incident) ?? "—"}
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      {reporterDisplayName(incident)}
                    </td>
                    <td className="px-3 py-2 text-muted-foreground">
                      {formatIncidentTimestamp(incident.createdAt)}
                    </td>
                    <td className="px-3 py-2">
                      <Link
                        href={`/organizations/${organizationId}/incidents/${incident.id}`}
                        className="text-primary hover:underline"
                      >
                        Open
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <p className="text-muted-foreground">
              Page {incidentPage.page + 1} of {incidentPage.totalPages} · {incidentPage.totalElements}{" "}
              incidents
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label="Previous page"
                disabled={filters.page <= 0}
                onClick={() => navigateFilters(withIncidentListPage(filters, filters.page - 1))}
              >
                Previous page
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label="Next page"
                disabled={filters.page >= incidentPage.totalPages - 1}
                onClick={() => navigateFilters(withIncidentListPage(filters, filters.page + 1))}
              >
                Next page
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
