import type { IncidentPostmortem, OrganizationPostmortemListStatus } from "@/types/incident";
import type { OrganizationRole } from "@/types/organization";
import { DEFAULT_POSTMORTEM_PAGE_SIZE, MAX_POSTMORTEM_PAGE_SIZE } from "@/lib/api/incident-postmortems";

export const ORGANIZATION_POSTMORTEMS_PAGE_SIZE = DEFAULT_POSTMORTEM_PAGE_SIZE;

export type PostmortemLibraryStatusFilter = "DEFAULT_PUBLISHED" | OrganizationPostmortemListStatus;

export type PostmortemLibraryFilterOption = {
  value: PostmortemLibraryStatusFilter;
  label: string;
};

const ALL_FILTER_OPTIONS: PostmortemLibraryFilterOption[] = [
  { value: "DEFAULT_PUBLISHED", label: "Published" },
  { value: "DRAFT", label: "Drafts" },
  { value: "ARCHIVED", label: "Archived" },
  { value: "ALL", label: "All statuses" },
];

/**
 * Organization list requires membership only ({@code requireMembership}); any member may use all status filters.
 */
export function getPostmortemLibraryFilterOptions(
  role: OrganizationRole,
): PostmortemLibraryFilterOption[] {
  void role;
  return ALL_FILTER_OPTIONS;
}

export function libraryStatusFilterToQueryStatus(
  filter: PostmortemLibraryStatusFilter,
): OrganizationPostmortemListStatus | undefined {
  return filter === "DEFAULT_PUBLISHED" ? undefined : filter;
}

export function clampPostmortemLibraryPage(page: number, totalPages: number): number {
  const safePage = Number.isFinite(page) ? Math.max(0, Math.floor(page)) : 0;
  if (totalPages <= 0) {
    return 0;
  }
  return Math.min(safePage, totalPages - 1);
}

export function clampPostmortemLibraryPageSize(size: number): number {
  if (!Number.isFinite(size)) {
    return ORGANIZATION_POSTMORTEMS_PAGE_SIZE;
  }
  return Math.min(Math.max(Math.floor(size), 1), MAX_POSTMORTEM_PAGE_SIZE);
}

export function incidentDetailPath(organizationId: string, incidentId: string): string {
  return `/organizations/${organizationId}/incidents/${incidentId}`;
}

export function summarizePostmortemText(text: string, maxLength = 200): string {
  const trimmed = text.trim();
  if (!trimmed) {
    return "";
  }
  if (trimmed.length <= maxLength) {
    return trimmed;
  }
  return `${trimmed.slice(0, maxLength - 1)}…`;
}

export function postmortemAuthorDisplayName(postmortem: IncidentPostmortem): string {
  const name = `${postmortem.authorFirstName} ${postmortem.authorLastName}`.trim();
  return name || postmortem.authorEmail;
}

export function postmortemPublisherDisplayName(postmortem: IncidentPostmortem): string | null {
  if (!postmortem.publishedById || !postmortem.publishedByEmail) {
    return null;
  }
  const name = `${postmortem.publishedByFirstName ?? ""} ${postmortem.publishedByLastName ?? ""}`.trim();
  return name || postmortem.publishedByEmail;
}

export function postmortemListTimestamp(postmortem: IncidentPostmortem): string | null {
  return postmortem.publishedAt ?? postmortem.updatedAt ?? postmortem.createdAt;
}

export function getPostmortemLibraryEmptyMessage(filter: PostmortemLibraryStatusFilter): string {
  switch (filter) {
    case "DEFAULT_PUBLISHED":
      return "No published postmortems yet. Publish a postmortem from a resolved incident to share it here.";
    case "DRAFT":
      return "No draft postmortems match this filter.";
    case "ARCHIVED":
      return "No archived postmortems match this filter.";
    case "ALL":
      return "No postmortems match this filter.";
    default:
      return "No postmortems match this filter.";
  }
}

export function shouldResetPageOnFilterChange(
  previous: PostmortemLibraryStatusFilter,
  next: PostmortemLibraryStatusFilter,
): boolean {
  return previous !== next;
}
