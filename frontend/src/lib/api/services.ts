import { getAuthenticatedClient } from "@/lib/api/authenticated-client";
import type { Service } from "@/types/organization";

export type CreateServiceInput = {
  name: string;
  slug: string;
  description?: string;
  teamId?: string;
};

export type UpdateServiceInput = {
  name?: string;
  slug?: string;
  description?: string;
  teamId?: string;
};

export async function listServices(organizationId: string): Promise<Service[]> {
  return getAuthenticatedClient().get<Service[]>(
    `/api/v1/organizations/${organizationId}/services`,
  );
}

export async function createService(
  organizationId: string,
  input: CreateServiceInput,
): Promise<Service> {
  return getAuthenticatedClient().post<Service>(
    `/api/v1/organizations/${organizationId}/services`,
    input,
  );
}

export async function getService(organizationId: string, serviceId: string): Promise<Service> {
  return getAuthenticatedClient().get<Service>(
    `/api/v1/organizations/${organizationId}/services/${serviceId}`,
  );
}

export async function updateService(
  organizationId: string,
  serviceId: string,
  input: UpdateServiceInput,
): Promise<Service> {
  return getAuthenticatedClient().patch<Service>(
    `/api/v1/organizations/${organizationId}/services/${serviceId}`,
    input,
  );
}

export async function deleteService(organizationId: string, serviceId: string): Promise<void> {
  await getAuthenticatedClient().delete(
    `/api/v1/organizations/${organizationId}/services/${serviceId}`,
  );
}
