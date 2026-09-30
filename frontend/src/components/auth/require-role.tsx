"use client";

import type { UserRole } from "@/types/auth";
import { useAuth } from "@/components/providers/auth-provider";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

export function RequireRole({ role, children }: { role: UserRole; children: ReactNode }) {
  const { user, isLoading, isAuthenticated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && isAuthenticated && user?.role !== role) {
      router.replace("/dashboard");
    }
  }, [user, role, isLoading, isAuthenticated, router]);

  if (isLoading || !user) {
    return null;
  }

  if (user.role !== role) {
    return null;
  }

  return children;
}
