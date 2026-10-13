"use client";

import { useAuth } from "@/components/providers/auth-provider";
import { listIncidents } from "@/lib/api/incidents";
import {
  incidentListFiltersQueryKeyPayload,
  incidentListFiltersToApiQuery,
  type IncidentListFilters,
} from "@/lib/incident-list-filters";
import { useQuery } from "@tanstack/react-query";

export const ORGANIZATION_INCIDENTS_QUERY_KEY = "incidents";

export function organizationIncidentsQueryKey(
  organizationId: string,
  filters: IncidentListFilters,
  userId?: string,
) {
  return [
    ORGANIZATION_INCIDENTS_QUERY_KEY,
    organizationId,
    incidentListFiltersQueryKeyPayload(filters),
    userId,
  ] as const;
}

export type UseOrganizationIncidentsQueryParams = {
  organizationId: string;
  filters: IncidentListFilters;
  enabled?: boolean;
};

export function useOrganizationIncidentsQuery({
  organizationId,
  filters,
  enabled = true,
}: UseOrganizationIncidentsQueryParams) {
  const { user } = useAuth();
  const apiQuery = incidentListFiltersToApiQuery(filters);

  return useQuery({
    queryKey: organizationIncidentsQueryKey(organizationId, filters, user?.id),
    queryFn: () => listIncidents(organizationId, apiQuery),
    enabled: Boolean(enabled && organizationId && user?.id),
  });
}
