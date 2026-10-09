"use client";

import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from "@/components/incident/incident-badges";
import { IncidentComments } from "@/components/incident/incident-comments";
import { IncidentPostmortemSection } from "@/components/incident/incident-postmortem-section";
import { IncidentTimeline } from "@/components/incident/incident-timeline";
import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getIncident, updateIncident } from "@/lib/api/incidents";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import {
  commanderDisplayName,
  formatIncidentTimestamp,
  isTerminalIncidentStatus,
  memberDisplayName,
  reporterDisplayName,
} from "@/lib/incident-display";
import {
  canCancelIncident,
  canManageOrganization,
  canUpdateIncident,
} from "@/lib/organization-rbac";
import type { Incident, IncidentSeverity, IncidentStatus, UpdateIncidentBody } from "@/types/incident";
import { useAuth } from "@/components/providers/auth-provider";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

const SEVERITIES: IncidentSeverity[] = ["SEV1", "SEV2", "SEV3", "SEV4"];

export default function IncidentDetailPage() {
  const params = useParams<{ organizationId: string; incidentId: string }>();
  const { organizationId, incidentId } = params;
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const [isEditing, setIsEditing] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("SEV3");
  const [serviceId, setServiceId] = useState("");
  const [commanderId, setCommanderId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const incidentQuery = useQuery({
    queryKey: ["incident", organizationId, incidentId],
    queryFn: () => getIncident(organizationId, incidentId),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
    enabled: isEditing,
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
    enabled: isEditing,
  });

  const organization = organizationQuery.data;
  const incident = incidentQuery.data;
  const role = organization?.currentUserRole ?? "VIEWER";
  const canUpdate = canUpdateIncident(role);
  const canCancel = canCancelIncident(role);
  const canManage = canManageOrganization(role);
  const terminal = incident ? isTerminalIncidentStatus(incident.status) : false;

  const invalidateIncident = async () => {
    await queryClient.invalidateQueries({ queryKey: ["incident", organizationId, incidentId] });
    await queryClient.invalidateQueries({ queryKey: ["incidents", organizationId] });
    await queryClient.invalidateQueries({ queryKey: ["incident-events", organizationId, incidentId] });
  };

  const statusMutation = useMutation({
    mutationFn: (status: IncidentStatus) =>
      updateIncident(organizationId, incidentId, { status }),
    onSuccess: async () => {
      setActionError(null);
      await invalidateIncident();
    },
    onError: (error) => {
      setActionError(getApiErrorMessage(error, "Failed to update status"));
    },
  });

  const updateMutation = useMutation({
    mutationFn: (body: UpdateIncidentBody) => updateIncident(organizationId, incidentId, body),
    onSuccess: async () => {
      setFormError(null);
      setIsEditing(false);
      await invalidateIncident();
    },
    onError: (error) => {
      setFormError(getApiErrorMessage(error, "Failed to update incident"));
    },
  });

  function startEditing(current: Incident) {
    setTitle(current.title);
    setDescription(current.description ?? "");
    setSeverity(current.severity);
    setServiceId(current.serviceId ?? "");
    setCommanderId(current.commanderId ?? "");
    setFormError(null);
    setIsEditing(true);
  }

  function buildUpdateBody(current: Incident): UpdateIncidentBody {
    const body: UpdateIncidentBody = {};
    const trimmedTitle = title.trim();
    if (trimmedTitle !== current.title) {
      body.title = trimmedTitle;
    }
    const trimmedDescription = description.trim();
    const currentDescription = current.description ?? "";
    if (trimmedDescription !== currentDescription) {
      body.description = trimmedDescription === "" ? null : trimmedDescription;
    }
    if (severity !== current.severity) {
      body.severity = severity;
    }
    const nextService = serviceId || null;
    if (nextService !== current.serviceId) {
      body.serviceId = nextService;
    }
    const nextCommander = commanderId || null;
    if (nextCommander !== current.commanderId) {
      body.commanderId = nextCommander;
    }
    return body;
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/organizations/${organizationId}/incidents`}
        className="text-sm text-primary hover:underline"
      >
        ← Back to incidents
      </Link>

      {incidentQuery.isPending && (
        <p className="text-sm text-muted-foreground">Loading incident…</p>
      )}
      {incidentQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(incidentQuery.error, "Failed to load incident")}
        </p>
      )}

      {incident && organization && (
        <>
          <OrganizationNav organizationId={organizationId} canManage={canManage} />

          <header className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-sm text-muted-foreground">{incident.displayId}</span>
              <IncidentSeverityBadge severity={incident.severity} />
              <IncidentStatusBadge status={incident.status} />
            </div>
            <h1 className="text-3xl font-semibold tracking-tight">{incident.title}</h1>
            {terminal && (
              <p className="text-sm text-muted-foreground">
                This incident is in a terminal state ({incident.status}) and cannot be edited.
              </p>
            )}
          </header>

          {canUpdate && !terminal && !isEditing && (
            <div className="flex flex-wrap gap-2">
              {incident.status === "OPEN" && (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate("ACKNOWLEDGED")}
                  >
                    Acknowledge
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate("RESOLVED")}
                  >
                    Resolve
                  </Button>
                  {canCancel && (
                    <Button
                      type="button"
                      variant="outline"
                      disabled={statusMutation.isPending}
                      onClick={() => statusMutation.mutate("CANCELLED")}
                    >
                      Cancel incident
                    </Button>
                  )}
                </>
              )}
              {incident.status === "ACKNOWLEDGED" && (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate("RESOLVED")}
                  >
                    Resolve
                  </Button>
                  {canCancel && (
                    <Button
                      type="button"
                      variant="outline"
                      disabled={statusMutation.isPending}
                      onClick={() => statusMutation.mutate("CANCELLED")}
                    >
                      Cancel incident
                    </Button>
                  )}
                </>
              )}
              <Button type="button" variant="secondary" onClick={() => startEditing(incident)}>
                Edit details
              </Button>
            </div>
          )}

          <FieldError message={actionError ?? undefined} />

          {isEditing && incident && (
            <form
              className="max-w-lg space-y-4 rounded-lg border p-4"
              onSubmit={(event) => {
                event.preventDefault();
                const body = buildUpdateBody(incident);
                if (Object.keys(body).length === 0) {
                  setFormError("No changes to save.");
                  return;
                }
                if (!title.trim()) {
                  setFormError("Title is required.");
                  return;
                }
                setFormError(null);
                updateMutation.mutate(body);
              }}
            >
              <div className="space-y-2">
                <Label htmlFor="edit-title">Title</Label>
                <Input
                  id="edit-title"
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  maxLength={200}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-severity">Severity</Label>
                <select
                  id="edit-severity"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={severity}
                  onChange={(event) => setSeverity(event.target.value as IncidentSeverity)}
                >
                  {SEVERITIES.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-description">Description</Label>
                <textarea
                  id="edit-description"
                  className="flex min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-service">Service</Label>
                <select
                  id="edit-service"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={serviceId}
                  onChange={(event) => setServiceId(event.target.value)}
                >
                  <option value="">No service</option>
                  {servicesQuery.data?.map((service) => (
                    <option key={service.id} value={service.id}>
                      {service.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-commander">Commander</Label>
                <select
                  id="edit-commander"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={commanderId}
                  onChange={(event) => setCommanderId(event.target.value)}
                >
                  <option value="">No commander</option>
                  {membersQuery.data?.map((member) => (
                    <option key={member.userId} value={member.userId}>
                      {memberDisplayName(member.firstName, member.lastName, member.email)}
                    </option>
                  ))}
                </select>
              </div>
              <FieldError message={formError ?? undefined} />
              <div className="flex gap-2">
                <Button type="submit" disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? "Saving…" : "Save changes"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setIsEditing(false);
                    setFormError(null);
                  }}
                >
                  Cancel
                </Button>
              </div>
            </form>
          )}

          {!isEditing && (
            <section className="grid gap-4 rounded-lg border p-4 text-sm sm:grid-cols-2">
              <div>
                <p className="font-medium text-muted-foreground">Description</p>
                <p className="mt-1 whitespace-pre-wrap">{incident.description ?? "—"}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Service</p>
                <p className="mt-1">{incident.serviceName ?? "No service"}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Reporter</p>
                <p className="mt-1">{reporterDisplayName(incident)}</p>
                <p className="text-xs text-muted-foreground">{incident.reporterEmail}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Commander</p>
                <p className="mt-1">{commanderDisplayName(incident) ?? "No commander"}</p>
                {incident.commanderEmail && (
                  <p className="text-xs text-muted-foreground">{incident.commanderEmail}</p>
                )}
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Created</p>
                <p className="mt-1">{formatIncidentTimestamp(incident.createdAt)}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Updated</p>
                <p className="mt-1">{formatIncidentTimestamp(incident.updatedAt)}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Acknowledged</p>
                <p className="mt-1">{formatIncidentTimestamp(incident.acknowledgedAt)}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Resolved</p>
                <p className="mt-1">{formatIncidentTimestamp(incident.resolvedAt)}</p>
              </div>
              <div>
                <p className="font-medium text-muted-foreground">Cancelled</p>
                <p className="mt-1">{formatIncidentTimestamp(incident.cancelledAt)}</p>
              </div>
            </section>
          )}

          <IncidentPostmortemSection
            organizationId={organizationId}
            incidentId={incidentId}
            organizationRole={role}
            incidentStatus={incident.status}
          />

          <IncidentComments
            organizationId={organizationId}
            incidentId={incidentId}
            organizationRole={role}
            incidentWritable={!terminal}
          />

          <IncidentTimeline organizationId={organizationId} incidentId={incidentId} />
        </>
      )}
    </div>
  );
}
