"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization, updateOrganization } from "@/lib/api/organizations";
import { canManageOrganization } from "@/lib/organization-rbac";
import type { Organization } from "@/types/organization";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

function OrganizationSettingsForm({
  organization,
  organizationId,
}: {
  organization: Organization;
  organizationId: string;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(organization.name);
  const [slug, setSlug] = useState(organization.slug);
  const [error, setError] = useState<string | null>(null);

  const updateMutation = useMutation({
    mutationFn: () => {
      const payload: { name?: string; slug?: string } = {};
      const trimmedName = name.trim();
      const trimmedSlug = slug.trim().toLowerCase();
      if (trimmedName !== organization.name) {
        payload.name = trimmedName;
      }
      if (trimmedSlug !== organization.slug) {
        payload.slug = trimmedSlug;
      }
      if (Object.keys(payload).length === 0) {
        throw new Error("No changes to save.");
      }
      return updateOrganization(organizationId, payload);
    },
    onSuccess: async (updated: Organization) => {
      setError(null);
      queryClient.setQueryData(["organization", organizationId], updated);
      await queryClient.invalidateQueries({ queryKey: ["organizations"] });
    },
    onError: (err) => {
      if (err instanceof Error && err.message === "No changes to save.") {
        setError(err.message);
        return;
      }
      setError(getApiErrorMessage(err, "Failed to update organization"));
    },
  });

  return (
    <form
      className="max-w-md space-y-3 rounded-lg border p-4"
      onSubmit={(event) => {
        event.preventDefault();
        setError(null);
        updateMutation.mutate();
      }}
    >
      <div className="space-y-2">
        <Label htmlFor="org-settings-name">Name</Label>
        <Input
          id="org-settings-name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          required
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="org-settings-slug">Slug</Label>
        <Input
          id="org-settings-slug"
          value={slug}
          onChange={(event) => setSlug(event.target.value)}
          required
        />
      </div>
      <FieldError message={error ?? undefined} />
      <Button type="submit" disabled={updateMutation.isPending}>
        {updateMutation.isPending ? "Saving…" : "Save changes"}
      </Button>
    </form>
  );
}

export default function OrganizationSettingsPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  if (organization && !canManage) {
    return (
      <div className="space-y-4">
        <Link
          href={`/organizations/${organizationId}`}
          className="text-sm text-primary hover:underline"
        >
          ← Back to organization
        </Link>
        <p className="text-sm text-muted-foreground">
          Organization settings require OWNER or ADMIN.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/organizations/${organizationId}`}
        className="text-sm text-primary hover:underline"
      >
        ← Back to organization
      </Link>

      {organization && (
        <>
          <section className="space-y-2">
            <h1 className="text-3xl font-semibold tracking-tight">Settings</h1>
            <p className="text-sm text-muted-foreground">{organization.name}</p>
          </section>

          <OrganizationNav organizationId={organizationId} canManage={canManage} />

          <OrganizationSettingsForm
            key={organization.updatedAt}
            organization={organization}
            organizationId={organizationId}
          />
        </>
      )}
    </div>
  );
}
