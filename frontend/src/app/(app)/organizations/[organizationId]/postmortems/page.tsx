"use client";

import { OrganizationNav } from "@/components/organization/organization-nav";
import { OrganizationPostmortemListItem } from "@/components/organization/organization-postmortem-list-item";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/components/providers/auth-provider";
import { getApiErrorMessage } from "@/lib/api/errors";
import { getOrganization } from "@/lib/api/organizations";
import { useOrganizationPostmortemsQuery } from "@/lib/hooks/use-incident-postmortem";
import {
  getPostmortemLibraryEmptyMessage,
  getPostmortemLibraryFilterOptions,
  libraryStatusFilterToQueryStatus,
  ORGANIZATION_POSTMORTEMS_PAGE_SIZE,
  type PostmortemLibraryStatusFilter,
} from "@/lib/organization-postmortems-library";
import { canManageOrganization } from "@/lib/organization-rbac";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";

export default function OrganizationPostmortemsPage() {
  const params = useParams<{ organizationId: string }>();
  const organizationId = params.organizationId;
  const { user } = useAuth();

  const [statusFilter, setStatusFilter] = useState<PostmortemLibraryStatusFilter>("DEFAULT_PUBLISHED");
  const [page, setPage] = useState(0);

  const organizationQuery = useQuery({
    queryKey: ["organization", organizationId, user?.id],
    queryFn: () => getOrganization(organizationId),
    enabled: Boolean(user?.id),
  });

  const organization = organizationQuery.data;
  const role = organization?.currentUserRole ?? "VIEWER";
  const canManage = organization ? canManageOrganization(role) : false;
  const filterOptions = getPostmortemLibraryFilterOptions(role);

  const postmortemsQuery = useOrganizationPostmortemsQuery({
    organizationId,
    page,
    size: ORGANIZATION_POSTMORTEMS_PAGE_SIZE,
    status: libraryStatusFilterToQueryStatus(statusFilter),
    enabled: Boolean(organizationId && user?.id && organization),
  });

  const postmortemPage = postmortemsQuery.data;
  const totalPages = postmortemPage?.totalPages ?? 0;

  const isInitialLoading =
    Boolean(organizationId && user?.id && organization) &&
    postmortemsQuery.isPending &&
    !postmortemPage;

  const showStatusBadge = statusFilter === "ALL";

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/organizations/${organizationId}`}
          className="text-sm text-primary hover:underline"
        >
          ← Back to organization
        </Link>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">Postmortems</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          Browse structured incident write-ups for this organization. Published postmortems appear
          here by default; open a record to read the full postmortem on the incident page.
        </p>
      </div>

      {organizationQuery.isError && (
        <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(organizationQuery.error, "Failed to load organization")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void organizationQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {organization && (
        <OrganizationNav organizationId={organizationId} canManage={canManage} />
      )}

      {organization && (
      <>
      <div className="flex flex-wrap items-end gap-4">
        <div className="space-y-2">
          <Label htmlFor="postmortem-status-filter">Status</Label>
          <select
            id="postmortem-status-filter"
            className="flex h-10 min-w-[12rem] rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={statusFilter}
            onChange={(event) => {
              const next = event.target.value as PostmortemLibraryStatusFilter;
              setStatusFilter(next);
              setPage(0);
            }}
          >
            {filterOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {isInitialLoading && (
        <p className="text-sm text-muted-foreground" role="status">Loading postmortems…</p>
      )}

      {postmortemsQuery.isError && (
        <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-4">
          <p className="text-sm text-destructive">
            {getApiErrorMessage(postmortemsQuery.error, "Failed to load postmortems")}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void postmortemsQuery.refetch()}>
            Retry
          </Button>
        </div>
      )}

      {!isInitialLoading && !postmortemsQuery.isError && postmortemPage?.totalElements === 0 && (
        <div className="rounded-lg border border-dashed p-8 text-center">
          <h2 className="text-lg font-medium">No postmortems to show</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {getPostmortemLibraryEmptyMessage(statusFilter)}
          </p>
        </div>
      )}

      {postmortemPage && postmortemPage.totalElements > 0 && !postmortemsQuery.isError && (
        <>
          <ul className="space-y-3" aria-label="Organization postmortems">
            {postmortemPage.content.map((postmortem) => (
              <li key={postmortem.id}>
                <OrganizationPostmortemListItem
                  organizationId={organizationId}
                  postmortem={postmortem}
                  showStatus={showStatusBadge}
                />
              </li>
            ))}
          </ul>

          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <p className="text-muted-foreground">
              Page {postmortemPage.page + 1} of {Math.max(postmortemPage.totalPages, 1)} ·{" "}
              {postmortemPage.totalElements} postmortems
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label="Previous page"
                disabled={page <= 0 || postmortemsQuery.isFetching}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                Previous page
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                aria-label="Next page"
                disabled={
                  totalPages === 0 || page >= totalPages - 1 || postmortemsQuery.isFetching
                }
                onClick={() => setPage((current) => current + 1)}
              >
                Next page
              </Button>
            </div>
          </div>
        </>
      )}
      </>
      )}
    </div>
  );
}
