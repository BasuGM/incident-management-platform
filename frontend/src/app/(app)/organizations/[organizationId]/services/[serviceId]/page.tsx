"use client";

import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization } from "@/lib/api/organizations";
import { deleteService, getService, updateService } from "@/lib/api/services";
import { listTeams } from "@/lib/api/teams";
import { canManageOrganization } from "@/lib/organization-rbac";
import { isValidServiceSlug, SERVICE_SLUG_VALIDATION_MESSAGE } from "@/lib/validation/service-slug";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

function formatTimestamp(value: string) {
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

export default function ServiceDetailPage() {
  const params = useParams<{ organizationId: string; serviceId: string }>();
  const { organizationId, serviceId } = params;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [isEditing, setIsEditing] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  /** Empty string means do not change team assignment in PATCH */
  const [teamId, setTeamId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const serviceQuery = useQuery({
    queryKey: ["service", organizationId, serviceId],
    queryFn: () => getService(organizationId, serviceId),
  });

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
    enabled:
      isEditing &&
      canManageOrganization(organizationQuery.data?.currentUserRole ?? "VIEWER"),
  });

  const service = serviceQuery.data;
  const canManage = organizationQuery.data
    ? canManageOrganization(organizationQuery.data.currentUserRole)
    : false;

  const updateMutation = useMutation({
    mutationFn: () => {
      if (!service) {
        throw new Error("Service not loaded");
      }
      const trimmedName = name.trim();
      const trimmedSlug = slug.trim();
      if (!trimmedName) {
        throw new Error("Name is required.");
      }
      if (!isValidServiceSlug(trimmedSlug)) {
        throw new Error(SERVICE_SLUG_VALIDATION_MESSAGE);
      }

      const payload: {
        name?: string;
        slug?: string;
        description?: string;
        teamId?: string;
      } = {};

      if (trimmedName !== service.name) {
        payload.name = trimmedName;
      }
      if (trimmedSlug !== service.slug) {
        payload.slug = trimmedSlug;
      }
      const nextDescription = description.trim();
      const currentDescription = service.description ?? "";
      if (nextDescription !== currentDescription) {
        payload.description = nextDescription;
      }
      if (teamId && teamId !== (service.teamId ?? "")) {
        payload.teamId = teamId;
      }

      if (Object.keys(payload).length === 0) {
        throw new Error("No changes to save.");
      }

      return updateService(organizationId, serviceId, payload);
    },
    onSuccess: async (updated) => {
      setFormError(null);
      setIsEditing(false);
      queryClient.setQueryData(["service", organizationId, serviceId], updated);
      await queryClient.invalidateQueries({ queryKey: ["services", organizationId] });
    },
    onError: (error) => {
      if (error instanceof Error && error.message === "No changes to save.") {
        setFormError(error.message);
        return;
      }
      if (error instanceof Error && error.message.startsWith("Slug must")) {
        setFormError(error.message);
        return;
      }
      setFormError(getApiErrorMessage(error, "Failed to update service"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteService(organizationId, serviceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["services", organizationId] });
      router.push(`/organizations/${organizationId}/services`);
    },
    onError: (error) => {
      setFormError(getApiErrorMessage(error, "Failed to delete service"));
    },
  });

  function startEditing() {
    if (!service) {
      return;
    }
    setName(service.name);
    setSlug(service.slug);
    setDescription(service.description ?? "");
    setTeamId("");
    setFormError(null);
    setIsEditing(true);
  }

  function handleDelete() {
    if (!window.confirm("Delete this service? This cannot be undone.")) {
      return;
    }
    setFormError(null);
    deleteMutation.mutate();
  }

  const serviceError = serviceQuery.isError ? serviceQuery.error : null;
  const serviceErrorStatus =
    serviceError instanceof ApiError ? serviceError.status : undefined;
  const serviceErrorMessage = serviceError
    ? getApiErrorMessage(serviceError, "Failed to load service")
    : null;

  return (
    <div className="space-y-8">
      <Link
        href={`/organizations/${organizationId}/services`}
        className="text-sm text-primary hover:underline"
      >
        ← Back to services
      </Link>

      {serviceQuery.isPending && (
        <p className="text-sm text-muted-foreground">Loading service…</p>
      )}

      {serviceError && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-destructive">
            {serviceErrorStatus === 403
              ? "You do not have access to this service."
              : serviceErrorStatus === 404
                ? "Service not found."
                : "Unable to load service"}
          </p>
          <p className="mt-1 text-muted-foreground">{serviceErrorMessage}</p>
        </div>
      )}

      {service && !isEditing && (
        <section className="space-y-4">
          <div className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight">{service.name}</h1>
            <p className="text-sm text-muted-foreground">Slug: {service.slug}</p>
            {service.description ? (
              <p className="text-sm text-muted-foreground">{service.description}</p>
            ) : (
              <p className="text-sm text-muted-foreground">No description.</p>
            )}
            <p className="text-sm text-muted-foreground">
              Owning team: {service.teamName ?? "—"}
            </p>
            <p className="text-sm text-muted-foreground">
              Created: {formatTimestamp(service.createdAt)}
            </p>
            <p className="text-sm text-muted-foreground">
              Updated: {formatTimestamp(service.updatedAt)}
            </p>
          </div>

          {canManage ? (
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={startEditing}>
                Edit service
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={deleteMutation.isPending}
                onClick={handleDelete}
              >
                {deleteMutation.isPending ? "Deleting…" : "Delete service"}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              Service management requires organization OWNER or ADMIN.
            </p>
          )}
          <FieldError message={formError ?? undefined} />
        </section>
      )}

      {service && isEditing && canManage && (
        <section className="max-w-lg space-y-4 rounded-lg border p-4">
          <h2 className="text-base font-semibold">Edit service</h2>
          <form
            className="space-y-3"
            onSubmit={(event) => {
              event.preventDefault();
              setFormError(null);
              if (!isValidServiceSlug(slug.trim())) {
                setFormError(SERVICE_SLUG_VALIDATION_MESSAGE);
                return;
              }
              updateMutation.mutate();
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="edit-service-name">Name</Label>
              <Input
                id="edit-service-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={100}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-service-slug">Slug</Label>
              <Input
                id="edit-service-slug"
                value={slug}
                onChange={(event) => setSlug(event.target.value)}
                maxLength={100}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-service-description">Description</Label>
              <Input
                id="edit-service-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                maxLength={500}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-service-team">Owning team</Label>
              <select
                id="edit-service-team"
                className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={teamId}
                onChange={(event) => setTeamId(event.target.value)}
              >
                <option value="">No change to team</option>
                {teamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
              <p className="text-xs text-muted-foreground">
                Current team: {service.teamName ?? "—"}. Clearing the owning team is not supported
                yet.
              </p>
            </div>
            <FieldError message={formError ?? undefined} />
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? "Saving…" : "Save changes"}
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={updateMutation.isPending}
                onClick={() => {
                  setIsEditing(false);
                  setFormError(null);
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        </section>
      )}
    </div>
  );
}
