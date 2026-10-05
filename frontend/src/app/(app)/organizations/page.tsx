"use client";

import { useOrganizations } from "@/components/organization/organization-provider";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { createOrganization } from "@/lib/api/organizations";
import { getApiErrorMessage } from "@/lib/api/errors";
import type { Organization } from "@/types/organization";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

function slugify(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

export default function OrganizationsPage() {
  const { organizations, isLoading } = useOrganizations();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [error, setError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: createOrganization,
    onSuccess: async (created) => {
      setError(null);
      await queryClient.cancelQueries({ queryKey: ["organizations"] });
      queryClient.setQueryData<Organization[]>(["organizations"], (existing) => {
        const current = existing ?? [];
        if (current.some((organization) => organization.id === created.id)) {
          return current;
        }
        return [...current, created];
      });
    },
    onError: (err) => setError(getApiErrorMessage(err, "Failed to create organization")),
  });

  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">Organizations</h1>
        <p className="text-sm text-muted-foreground">
          Multi-tenant workspaces. You only see organizations where you are a member.
        </p>
      </section>

      <section className="rounded-lg border bg-card p-6">
        <h2 className="text-base font-semibold">Create organization</h2>
        <form
          className="mt-4 grid gap-4 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault();
            const payload = {
              name: name.trim(),
              slug: (slug || slugify(name)).trim(),
            };
            setName("");
            setSlug("");
            setError(null);
            createMutation.mutate(payload);
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="org-name">Name</Label>
            <Input
              id="org-name"
              value={name}
              onChange={(event) => {
                setName(event.target.value);
                if (!slug) {
                  setSlug(slugify(event.target.value));
                }
              }}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="org-slug">Slug</Label>
            <Input
              id="org-slug"
              value={slug}
              onChange={(event) => setSlug(event.target.value)}
              required
            />
          </div>
          <div className="sm:col-span-2">
            <FieldError message={error ?? undefined} />
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Creating…" : "Create organization"}
            </Button>
          </div>
        </form>
      </section>

      <section className="space-y-4">
        <h2 className="text-base font-semibold">Your organizations</h2>
        {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
        {!isLoading && organizations.length === 0 && (
          <p className="text-sm text-muted-foreground">No organizations yet.</p>
        )}
        <div className="overflow-x-auto rounded-lg border">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Organization</th>
                <th className="px-4 py-3 font-medium">Slug</th>
                <th className="px-4 py-3 font-medium">Your role</th>
              </tr>
            </thead>
            <tbody>
              {organizations.map((organization) => (
                <tr key={organization.id} className="border-b last:border-b-0">
                  <td className="px-4 py-3">
                    <Link
                      href={`/organizations/${organization.id}`}
                      className="font-medium text-primary hover:underline"
                    >
                      {organization.name}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{organization.slug}</td>
                  <td className="px-4 py-3">{organization.currentUserRole}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
