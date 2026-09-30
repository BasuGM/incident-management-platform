"use client";

import { RequireRole } from "@/components/auth/require-role";
import { listUsers } from "@/lib/api/users";
import { getApiErrorMessage } from "@/lib/api/errors";
import { useQuery } from "@tanstack/react-query";

function UsersTable() {
  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: listUsers,
  });

  if (usersQuery.isPending) {
    return <p className="text-sm text-muted-foreground">Loading users…</p>;
  }

  if (usersQuery.isError) {
    return (
      <p className="text-sm text-destructive">
        {getApiErrorMessage(usersQuery.error, "Unable to load users")}
      </p>
    );
  }

  if (!usersQuery.data?.length) {
    return <p className="text-sm text-muted-foreground">No users found.</p>;
  }

  return (
    <div className="overflow-x-auto rounded-lg border">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b bg-muted/40 text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">Name</th>
            <th className="px-4 py-3 font-medium">Email</th>
            <th className="px-4 py-3 font-medium">Role</th>
            <th className="px-4 py-3 font-medium">Status</th>
            <th className="px-4 py-3 font-medium">Created At</th>
          </tr>
        </thead>
        <tbody>
          {usersQuery.data.map((user) => (
            <tr key={user.id} className="border-b last:border-b-0">
              <td className="px-4 py-3">{user.firstName} {user.lastName}</td>
              <td className="px-4 py-3">{user.email}</td>
              <td className="px-4 py-3">{user.role}</td>
              <td className="px-4 py-3">{user.enabled ? "Active" : "Disabled"}</td>
              <td className="px-4 py-3">
                {user.createdAt ? new Date(user.createdAt).toLocaleString() : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function UsersPage() {
  return (
    <RequireRole role="ADMIN">
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Users</h1>
          <p className="mt-2 text-sm text-muted-foreground">Admin user directory.</p>
        </div>
        <UsersTable />
      </div>
    </RequireRole>
  );
}
