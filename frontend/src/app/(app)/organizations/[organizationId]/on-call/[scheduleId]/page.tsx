"use client";

import { OnCallScheduleDetailContent } from "@/app/(app)/organizations/[organizationId]/on-call/[scheduleId]/on-call-schedule-detail-content";
import { useParams } from "next/navigation";

export default function OnCallScheduleDetailPage() {
  const params = useParams<{ organizationId: string; scheduleId: string }>();
  return (
    <OnCallScheduleDetailContent
      organizationId={params.organizationId}
      scheduleId={params.scheduleId}
    />
  );
}
