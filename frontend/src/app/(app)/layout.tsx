import { RequireAuth } from "@/components/auth/require-auth";
import { OrganizationProvider } from "@/components/organization/organization-provider";
import { AppShell } from "@/components/layout/app-shell";

export default function ProtectedLayout({ children }: LayoutProps<"/">) {
  return (
    <RequireAuth>
      <OrganizationProvider>
        <AppShell>{children}</AppShell>
      </OrganizationProvider>
    </RequireAuth>
  );
}
