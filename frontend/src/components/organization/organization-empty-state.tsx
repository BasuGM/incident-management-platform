import { buttonVariants } from "@/components/ui/button";
import Link from "next/link";

type OrganizationEmptyStateProps = {
  showCreateLink?: boolean;
};

export function OrganizationEmptyState({ showCreateLink = true }: OrganizationEmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed bg-muted/20 p-8 text-center">
      <h2 className="text-lg font-semibold">No organizations yet</h2>
      <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
        You aren&apos;t a member of any organization. Create one to start managing teams and
        services, or ask an organization owner or admin to add you with your user ID.
      </p>
      {showCreateLink && (
        <Link
          href="/organizations#create-organization"
          className={buttonVariants({ variant: "default", className: "mt-4 inline-flex" })}
        >
          Create organization
        </Link>
      )}
    </div>
  );
}
