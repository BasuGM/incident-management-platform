"use client";

import { ApiStatusCard } from "@/components/home/api-status-card";
import { OrganizationEmptyState } from "@/components/organization/organization-empty-state";
import { useOrganizations } from "@/components/organization/organization-provider";
import { useAuth } from "@/components/providers/auth-provider";
import { buttonVariants } from "@/components/ui/button";
import Link from "next/link";

export default function DashboardPage() {
  const { user } = useAuth();
  const { organizations, isLoading } = useOrganizations();

  return (
    <div className="space-y-8">
      <section className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
        {user && (
          <p className="text-sm text-muted-foreground">
            Welcome back, {user.firstName}. Your account role is {user.role}. Open an organization
            below to manage teams and services.
          </p>
        )}
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-base font-semibold">Your organizations</h2>
          <Link href="/organizations" className="text-sm text-primary hover:underline">
            View all
          </Link>
        </div>
        {isLoading && <p className="text-sm text-muted-foreground">Loading organizations…</p>}
        {!isLoading && organizations.length === 0 && <OrganizationEmptyState />}
        {!isLoading && organizations.length > 0 && (
          <ul className="grid gap-3 sm:grid-cols-2">
            {organizations.map((organization) => (
              <li key={organization.id} className="rounded-lg border p-4">
                <h3 className="font-medium">{organization.name}</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Role in this organization: {organization.currentUserRole}
                </p>
                <Link
                  href={`/organizations/${organization.id}`}
                  className={buttonVariants({
                    variant: "outline",
                    size: "sm",
                    className: "mt-3 inline-flex",
                  })}
                >
                  Open organization
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="max-w-xl">
        <ApiStatusCard />
      </section>
    </div>
  );
}
