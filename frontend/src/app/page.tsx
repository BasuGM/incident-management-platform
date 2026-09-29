import { ApiStatusCard } from "@/components/home/api-status-card";

export default function Home() {
  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h1 className="text-3xl font-semibold tracking-tight">
          Developer Incident Management Platform
        </h1>
        <p className="max-w-2xl text-muted-foreground">
          Foundation workspace for a developer-focused incident management product.
          Domain features (incidents, teams, on-call, and integrations) are intentionally
          not implemented in this phase.
        </p>
      </section>

      <ApiStatusCard />
    </div>
  );
}
