"use client";

import { useAuth } from "@/components/providers/auth-provider";

export default function ProfilePage() {
  const { user } = useAuth();

  if (!user) {
    return null;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-semibold tracking-tight">Profile</h1>
      <dl className="grid max-w-lg gap-3 text-sm">
        <div className="flex justify-between gap-4 border-b pb-2">
          <dt className="text-muted-foreground">Name</dt>
          <dd className="font-medium">{user.firstName} {user.lastName}</dd>
        </div>
        <div className="flex justify-between gap-4 border-b pb-2">
          <dt className="text-muted-foreground">Email</dt>
          <dd className="font-medium">{user.email}</dd>
        </div>
        <div className="flex justify-between gap-4 border-b pb-2">
          <dt className="text-muted-foreground">Role</dt>
          <dd className="font-medium">{user.role}</dd>
        </div>
      </dl>
    </div>
  );
}
