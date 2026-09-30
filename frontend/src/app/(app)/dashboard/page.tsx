import { ApiStatusCard } from "@/components/home/api-status-card";

export default function DashboardPage() {
  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
        <p className="max-w-2xl text-muted-foreground">
          Authenticated workspace placeholder. Incident workflows will be added in later phases.
        </p>
      </section>
      <ApiStatusCard />
    </div>
  );
}
