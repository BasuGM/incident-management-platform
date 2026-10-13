import { IncidentsListContent } from "@/app/(app)/organizations/[organizationId]/incidents/incidents-list-content";
import { Suspense } from "react";

type PageProps = {
  params: Promise<{ organizationId: string }>;
};

export default async function OrganizationIncidentsPage({ params }: PageProps) {
  const { organizationId } = await params;

  return (
    <Suspense
      fallback={<p className="text-sm text-muted-foreground">Loading incidents…</p>}
    >
      <IncidentsListContent organizationId={organizationId} />
    </Suspense>
  );
}
