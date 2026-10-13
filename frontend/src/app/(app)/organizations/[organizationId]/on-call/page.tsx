"use client";

import { OnCallSchedulesListContent } from "@/app/(app)/organizations/[organizationId]/on-call/on-call-schedules-list-content";
import { useParams } from "next/navigation";

export default function OrganizationOnCallSchedulesPage() {
  const params = useParams<{ organizationId: string }>();
  return <OnCallSchedulesListContent organizationId={params.organizationId} />;
}
