"use client";

import { listOrganizations } from "@/lib/api/organizations";
import type { Organization } from "@/types/organization";
import { useQuery } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

const STORAGE_KEY = "selectedOrganizationId";

type OrganizationContextValue = {
  organizations: Organization[];
  selectedOrganization: Organization | null;
  selectedOrganizationId: string | null;
  setSelectedOrganizationId: (organizationId: string) => void;
  isLoading: boolean;
  refreshOrganizations: () => Promise<void>;
};

const OrganizationContext = createContext<OrganizationContextValue | null>(null);

function readStoredOrganizationId(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(STORAGE_KEY);
}

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const [manualSelection, setManualSelection] = useState<string | null>(readStoredOrganizationId);

  const organizationsQuery = useQuery({
    queryKey: ["organizations"],
    queryFn: listOrganizations,
    staleTime: 0,
  });

  const organizations = useMemo(
    () => organizationsQuery.data ?? [],
    [organizationsQuery.data],
  );

  const selectedOrganizationId = useMemo(() => {
    if (!organizations.length) {
      return null;
    }
    if (manualSelection && organizations.some((org) => org.id === manualSelection)) {
      return manualSelection;
    }
    return organizations[0].id;
  }, [organizations, manualSelection]);

  useEffect(() => {
    if (selectedOrganizationId) {
      window.localStorage.setItem(STORAGE_KEY, selectedOrganizationId);
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }, [selectedOrganizationId]);

  const setSelectedOrganizationId = useCallback((organizationId: string) => {
    setManualSelection(organizationId);
  }, []);

  const selectedOrganization = useMemo(
    () => organizations.find((organization) => organization.id === selectedOrganizationId) ?? null,
    [organizations, selectedOrganizationId],
  );

  const value = useMemo(
    () => ({
      organizations,
      selectedOrganization,
      selectedOrganizationId,
      setSelectedOrganizationId,
      isLoading: organizationsQuery.isLoading,
      refreshOrganizations: async () => {
        await organizationsQuery.refetch();
      },
    }),
    [
      organizations,
      selectedOrganization,
      selectedOrganizationId,
      setSelectedOrganizationId,
      organizationsQuery,
    ],
  );

  return <OrganizationContext.Provider value={value}>{children}</OrganizationContext.Provider>;
}

export function useOrganizations() {
  const context = useContext(OrganizationContext);
  if (!context) {
    throw new Error("useOrganizations must be used within OrganizationProvider");
  }
  return context;
}
