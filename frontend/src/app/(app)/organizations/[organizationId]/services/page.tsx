"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization } from "@/lib/api/organizations";
import { createService, listServices } from "@/lib/api/services";
import { listTeams } from "@/lib/api/teams";
import { canManageOrganization } from "@/lib/organization-rbac";
import { isValidServiceSlug, SERVICE_SLUG_VALIDATION_MESSAGE } from "@/lib/validation/service-slug";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function OrganizationServicesPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const queryClient = useQueryClient();

  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [teamId, setTeamId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const servicesQuery = useQuery({
    queryKey: ["services", organizationId],
    queryFn: () => listServices(organizationId),
  });

  const teamsQuery = useQuery({
    queryKey: ["teams", organizationId],
    queryFn: () => listTeams(organizationId),
    enabled: canManageOrganization(organizationQuery.data?.currentUserRole ?? "VIEWER"),
  });

  const createMutation = useMutation({
    mutationFn: () => {
      const trimmedName = name.trim();
      const trimmedSlug = slug.trim();
      if (!trimmedName) {
        throw new Error("Name is required.");
      }
      if (!isValidServiceSlug(trimmedSlug)) {
        throw new Error(SERVICE_SLUG_VALIDATION_MESSAGE);
      }
      return createService(organizationId, {
        name: trimmedName,
        slug: trimmedSlug,
        description: description.trim() || undefined,
        teamId: teamId || undefined,
      });
    },
    onSuccess: async () => {
      setName("");
      setSlug("");
      setDescription("");
      setTeamId("");
      setFormError(null);
      await queryClient.invalidateQueries({ queryKey: ["services", organizationId] });
    },
    onError: (error) => {
      if (error instanceof Error && error.message.startsWith("Slug must")) {
        setFormError(error.message);
        return;
      }
      if (error instanceof Error && error.message === "Name is required.") {
        setFormError(error.message);
        return;
      }
      setFormError(getApiErrorMessage(error, "Failed to create service"));
    },
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  const listError =
    servicesQuery.isError
      ? getApiErrorMessage(servicesQuery.error, "Failed to load services")
      : null;

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/organizations/${organizationId}`}
          className="text-sm text-primary hover:underline"
        >
          ← Back to organization
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Services</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Service catalog for this organization.
        </p>
      </div>

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

      {organizationQuery.isPending && (
        <p className="text-sm text-muted-foreground">Loading organization…</p>
      )}
      {organizationQuery.isError && (
        <p className="text-sm text-destructive">
          {getApiErrorMessage(organizationQuery.error, "Failed to load organization")}
        </p>
      )}

      {canManage && (
        <section className="rounded-lg border p-4">
          <h2 className="text-base font-semibold">Create service</h2>
          <form
            className="mt-4 grid gap-3 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault();
              setFormError(null);
              const trimmedSlug = slug.trim();
              if (!isValidServiceSlug(trimmedSlug)) {
                setFormError(SERVICE_SLUG_VALIDATION_MESSAGE);
                return;
              }
              createMutation.mutate();
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="service-name">Name</Label>
              <Input
                id="service-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                maxLength={100}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="service-slug">Slug</Label>
              <Input
                id="service-slug"
                value={slug}
                onChange={(event) => setSlug(event.target.value)}
                maxLength={100}
                required
                pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
                title={SERVICE_SLUG_VALIDATION_MESSAGE}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="service-description">Description</Label>
              <Input
                id="service-description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                maxLength={500}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="service-team">Owning team (optional)</Label>
              <select
                id="service-team"
                className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={teamId}
                onChange={(event) => setTeamId(event.target.value)}
              >
                <option value="">No team</option>
                {teamsQuery.data?.map((team) => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="sm:col-span-2">
              <FieldError message={formError ?? undefined} />
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? "Creating…" : "Create service"}
              </Button>
            </div>
          </form>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-base font-semibold">All services</h2>
        {servicesQuery.isPending && (
          <p className="text-sm text-muted-foreground">Loading services…</p>
        )}
        {listError && <p className="text-sm text-destructive">{listError}</p>}
        {!servicesQuery.isPending && !listError && servicesQuery.data?.length === 0 && (
          <p className="text-sm text-muted-foreground">No services yet.</p>
        )}
        <ul className="space-y-2 text-sm">
          {servicesQuery.data?.map((service) => (
            <li key={service.id} className="rounded border px-3 py-2">
              <Link
                href={`/organizations/${organizationId}/services/${service.id}`}
                className="font-medium text-primary hover:underline"
              >
                {service.name}
              </Link>
              <p className="text-muted-foreground">Slug: {service.slug}</p>
              {service.description ? (
                <p className="text-muted-foreground">{service.description}</p>
              ) : null}
              {service.teamName ? (
                <p className="text-muted-foreground">Team: {service.teamName}</p>
              ) : (
                <p className="text-muted-foreground">Team: —</p>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
