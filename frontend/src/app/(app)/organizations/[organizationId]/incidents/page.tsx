"use client";

import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from "@/components/incident/incident-badges";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button, buttonVariants } from "@/components/ui/button";
import { getApiErrorMessage } from "@/lib/api/errors";
import { listIncidents } from "@/lib/api/incidents";
import { getOrganization } from "@/lib/api/organizations";
import {
  commanderDisplayName,
  formatIncidentTimestamp,
  reporterDisplayName,
} from "@/lib/incident-display";
import { canCreateIncident, canManageOrganization } from "@/lib/organization-rbac";
import { useAuth } from "@/components/providers/auth-provider";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

const PAGE_SIZE = 20;

export default function OrganizationIncidentsPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const [page, setPage] = useState(0);
  const { user } = useAuth();

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const incidentsQuery = useQuery({
    queryKey: ["incidents", organizationId, page, PAGE_SIZE],
    queryFn: () => listIncidents(organizationId, page, PAGE_SIZE),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;
  const canCreate = organization ? canCreateIncident(organization.currentUserRole) : false;

  const incidentPage = incidentsQuery.data;
  const isEmpty = incidentPage && incidentPage.totalElements === 0;

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

      {incidentsQuery.isPending && (
        <p className="text-sm text-muted-foreground">Loading incidents…</p>
      )}
      {incidentsQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(incidentsQuery.error, "Failed to load incidents")}
        </p>
      )}

      {isEmpty && (
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
                disabled={page <= 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous page
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label="Next page"
                disabled={page >= incidentPage.totalPages - 1}
                onClick={() => setPage((current) => current + 1)}
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
