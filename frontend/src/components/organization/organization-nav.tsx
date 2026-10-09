"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

type OrganizationNavProps = {
  organizationId: string;
  canManage?: boolean;
};

const links = (organizationId: string, canManage: boolean) =>
  [
    { href: `/organizations/${organizationId}`, label: "Overview", exact: true },
    { href: `/organizations/${organizationId}/teams`, label: "Teams", exact: false },
    { href: `/organizations/${organizationId}/services`, label: "Services", exact: false },
    { href: `/organizations/${organizationId}/incidents`, label: "Incidents", exact: false },
    { href: `/organizations/${organizationId}/postmortems`, label: "Postmortems", exact: false },
    { href: `/organizations/${organizationId}/members`, label: "Members", exact: false },
    canManage
      ? { href: `/organizations/${organizationId}/settings`, label: "Settings", exact: false }
      : null,
  ].filter((link): link is { href: string; label: string; exact: boolean } => link !== null);

export function OrganizationNav({ organizationId, canManage = false }: OrganizationNavProps) {
  const pathname = usePathname();

  return (
    <nav className="flex flex-wrap gap-2 border-b pb-3 text-sm">
      {links(organizationId, canManage).map((link) => {
        const isActive = link.exact
          ? pathname === link.href
          : pathname === link.href || pathname.startsWith(`${link.href}/`);
        return (
          <Link
            key={link.href}
            href={link.href}
            className={
              isActive
                ? "rounded-md bg-muted px-3 py-1.5 font-medium text-foreground"
                : "rounded-md px-3 py-1.5 text-muted-foreground hover:bg-muted/60 hover:text-foreground"
            }
          >
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}
