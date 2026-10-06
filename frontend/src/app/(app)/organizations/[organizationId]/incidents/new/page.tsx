"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getApiErrorMessage } from "@/lib/api/errors";
import { createIncident } from "@/lib/api/incidents";
import { getOrganization, listOrganizationMembers } from "@/lib/api/organizations";
import { listServices } from "@/lib/api/services";
import { memberDisplayName } from "@/lib/incident-display";
import { canCreateIncident, canManageOrganization } from "@/lib/organization-rbac";
import type { IncidentSeverity } from "@/types/incident";
import { useAuth } from "@/components/providers/auth-provider";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

const SEVERITIES: IncidentSeverity[] = ["SEV1", "SEV2", "SEV3", "SEV4"];

export default function CreateIncidentPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<IncidentSeverity>("SEV3");
  const [serviceId, setServiceId] = useState("");
  const [commanderId, setCommanderId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
  });

  const organization = organizationQuery.data;
  const canCreate = organization ? canCreateIncident(organization.currentUserRole) : false;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  const createMutation = useMutation({
    mutationFn: () => {
      const trimmedTitle = title.trim();
      if (!trimmedTitle) {
        throw new Error("Title is required.");
      }
      return createIncident(organizationId, {
        title: trimmedTitle,
        description: description.trim() || undefined,
        severity,
        serviceId: serviceId || undefined,
        commanderId: commanderId || undefined,
      });
    },
    onSuccess: async (incident) => {
      await queryClient.invalidateQueries({ queryKey: ["incidents", organizationId] });
      router.push(`/organizations/${organizationId}/incidents/${incident.id}`);
    },
    onError: (error) => {
      if (error instanceof Error && error.message === "Title is required.") {
        setFormError(error.message);
        return;
      }
      setFormError(getApiErrorMessage(error, "Failed to create incident"));
    },
  });

  if (organization && !canCreate) {
    return (
      <div className="space-y-4">
        <Link
          href={`/organizations/${organizationId}/incidents`}
          className="text-sm text-primary hover:underline"
        >
          ← Back to incidents
        </Link>
        <p className="text-sm text-muted-foreground">You do not have permission to create incidents.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/organizations/${organizationId}/incidents`}
        className="text-sm text-primary hover:underline"
      >
        ← Back to incidents
      </Link>

      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Create incident</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          You will be recorded as the reporter. New incidents start in OPEN status.
        </p>
      </div>

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

      <form
        className="max-w-lg space-y-4 rounded-lg border p-4"
        onSubmit={(event) => {
          event.preventDefault();
          setFormError(null);
          createMutation.mutate();
        }}
      >
        <div className="space-y-2">
          <Label htmlFor="incident-title">Title</Label>
          <Input
            id="incident-title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            required
            maxLength={200}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="incident-severity">Severity</Label>
          <select
            id="incident-severity"
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
          <Label htmlFor="incident-description">Description</Label>
          <textarea
            id="incident-description"
            className="flex min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={10000}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="incident-service">Service (optional)</Label>
          <select
            id="incident-service"
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
          {servicesQuery.data?.length === 0 && canManage && (
            <p className="text-xs text-muted-foreground">
              No services yet.{" "}
              <Link
                href={`/organizations/${organizationId}/services`}
                className="text-primary hover:underline"
              >
                Add services
              </Link>
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="incident-commander">Commander (optional)</Label>
          <select
            id="incident-commander"
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={commanderId}
            onChange={(event) => setCommanderId(event.target.value)}
          >
            <option value="">No commander</option>
            {membersQuery.data?.map((member) => (
              <option key={member.userId} value={member.userId}>
                {memberDisplayName(member.firstName, member.lastName, member.email)} ({member.role})
              </option>
            ))}
          </select>
        </div>

        <FieldError message={formError ?? undefined} />
        <Button type="submit" disabled={createMutation.isPending}>
          {createMutation.isPending ? "Creating…" : "Create incident"}
        </Button>
      </form>
    </div>
  );
}
