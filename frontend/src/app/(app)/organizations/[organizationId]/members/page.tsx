"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { OrganizationRoleHint } from "@/components/organization/organization-role-hint";
import { useAuth } from "@/components/providers/auth-provider";
import { Button } from "@/components/ui/button";
import { FieldError } from "@/components/ui/field-error";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { getApiErrorMessage } from "@/lib/api/errors";
import {
  addOrganizationMember,
  getOrganization,
  listOrganizationMembers,
  removeOrganizationMember,
  updateOrganizationMember,
} from "@/lib/api/organizations";
import { canManageOrganization } from "@/lib/organization-rbac";
import type { OrganizationRole } from "@/types/organization";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

const ASSIGNABLE_ROLES: OrganizationRole[] = ["ADMIN", "MEMBER", "VIEWER"];

export default function OrganizationMembersPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [userId, setUserId] = useState("");
  const [newMemberRole, setNewMemberRole] = useState<OrganizationRole>("MEMBER");
  const [formError, setFormError] = useState<string | null>(null);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId],
    queryFn: () => getOrganization(organizationId),
  });

  const membersQuery = useQuery({
    queryKey: ["organization-members", organizationId],
    queryFn: () => listOrganizationMembers(organizationId),
  });

  const organization = organizationQuery.data;
  const canManage = organization ? canManageOrganization(organization.currentUserRole) : false;

  const addMutation = useMutation({
    mutationFn: () => addOrganizationMember(organizationId, { userId: userId.trim(), role: newMemberRole }),
    onSuccess: async () => {
      setUserId("");
      setNewMemberRole("MEMBER");
      setFormError(null);
      await queryClient.invalidateQueries({ queryKey: ["organization-members", organizationId] });
    },
    onError: (error) => setFormError(getApiErrorMessage(error, "Failed to add member")),
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({ memberUserId, role }: { memberUserId: string; role: OrganizationRole }) =>
      updateOrganizationMember(organizationId, memberUserId, { role }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["organization-members", organizationId] });
    },
  });

  const removeMutation = useMutation({
    mutationFn: (memberUserId: string) => removeOrganizationMember(organizationId, memberUserId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["organization-members", organizationId] });
    },
  });

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
            <h1 className="text-3xl font-semibold tracking-tight">Members</h1>
            <p className="text-sm text-muted-foreground">{organization.name}</p>
            <OrganizationRoleHint organizationRole={organization.currentUserRole} />
          </section>

          <OrganizationNav organizationId={organizationId} canManage={canManage} />

          {canManage && (
            <section className="rounded-lg border p-4">
              <h2 className="text-base font-semibold">Add member</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Add an existing platform user by their user ID. They must already have an account;
                there is no email invitation flow.
              </p>
              <form
                className="mt-4 grid gap-3 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  setFormError(null);
                  addMutation.mutate();
                }}
              >
                <div className="space-y-2">
                  <Label htmlFor="member-user-id">User ID</Label>
                  <Input
                    id="member-user-id"
                    value={userId}
                    onChange={(event) => setUserId(event.target.value)}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="member-role">Organization role</Label>
                  <select
                    id="member-role"
                    className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm"
                    value={newMemberRole}
                    onChange={(event) => setNewMemberRole(event.target.value as OrganizationRole)}
                  >
                    {ASSIGNABLE_ROLES.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="sm:col-span-2">
                  <FieldError message={formError ?? undefined} />
                  <Button type="submit" disabled={addMutation.isPending}>
                    {addMutation.isPending ? "Adding…" : "Add member"}
                  </Button>
                </div>
              </form>
            </section>
          )}

          <section className="space-y-3">
            <h2 className="text-base font-semibold">All members</h2>
            {membersQuery.isPending && (
              <p className="text-sm text-muted-foreground">Loading members…</p>
            )}
            <ul className="space-y-2 text-sm">
              {membersQuery.data?.map((member) => {
                const isSelf = user?.id === member.userId;
                const isOwner = member.role === "OWNER";
                const canEditRow = canManage && !isSelf && !isOwner;

                return (
                  <li
                    key={member.id}
                    className="flex flex-col gap-2 rounded border px-3 py-3 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p className="font-medium">
                        {member.firstName} {member.lastName}
                      </p>
                      <p className="text-muted-foreground">{member.email}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {canEditRow ? (
                        <select
                          className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                          value={member.role}
                          disabled={updateRoleMutation.isPending}
                          onChange={(event) =>
                            updateRoleMutation.mutate({
                              memberUserId: member.userId,
                              role: event.target.value as OrganizationRole,
                            })
                          }
                        >
                          {ASSIGNABLE_ROLES.map((role) => (
                            <option key={role} value={role}>
                              {role}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-muted-foreground">{member.role}</span>
                      )}
                      {canEditRow && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={removeMutation.isPending}
                          onClick={() => {
                            if (
                              window.confirm(
                                `Remove ${member.firstName} ${member.lastName} from this organization?`,
                              )
                            ) {
                              removeMutation.mutate(member.userId);
                            }
                          }}
                        >
                          Remove
                        </Button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
            {!canManage && (
              <p className="text-sm text-muted-foreground">
                Member management requires organization OWNER or ADMIN.
              </p>
            )}
          </section>
        </>
      )}
    </div>
  );
}
