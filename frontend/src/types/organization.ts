export type OrganizationRole = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER";

export type Organization = {
  id: string;
  name: string;
  slug: string;
  currentUserRole: OrganizationRole;
  createdAt: string;
  updatedAt: string;
};

export type OrganizationMember = {
  id: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: OrganizationRole;
  createdAt: string;
};

export type Team = {
  id: string;
  organizationId: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TeamMember = {
  id: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  createdAt: string;
};

export type Service = {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  description: string | null;
  teamId: string | null;
  teamName: string | null;
  createdAt: string;
  updatedAt: string;
};
