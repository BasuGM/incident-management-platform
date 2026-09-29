"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchHealth } from "@/lib/api/health";
import { Button } from "@/components/ui/button";

export function ApiStatusCard() {
  const healthQuery = useQuery({
    queryKey: ["health"],
    queryFn: fetchHealth,
  });

  return (
    <section className="rounded-lg border bg-card p-6 text-card-foreground shadow-sm">
      <h2 className="text-base font-semibold">API status</h2>
      <p className="mt-2 text-sm text-muted-foreground">
        Calls <code className="rounded bg-muted px-1 py-0.5">GET /api/v1/health</code>{" "}
        using the shared API client and TanStack Query.
      </p>

      <div className="mt-4 text-sm">
        {healthQuery.isPending && <p className="text-muted-foreground">Checking backend…</p>}
        {healthQuery.isError && (
          <p className="text-destructive">
            Backend unreachable. Start Docker Compose and the Spring Boot API, then retry.
          </p>
        )}
        {healthQuery.isSuccess && (
          <p>
            <span className="font-medium">{healthQuery.data.service}</span> is{" "}
            <span className="font-medium">{healthQuery.data.status}</span>
          </p>
        )}
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        className="mt-4"
        onClick={() => healthQuery.refetch()}
        disabled={healthQuery.isFetching}
      >
        Refresh status
      </Button>
    </section>
  );
}
