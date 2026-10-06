import { expect, test, type APIRequestContext } from "@playwright/test";

const password = "Password1";
const apiBase = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function registerAndLogin(
  request: APIRequestContext,
  email: string,
  firstName: string,
  lastName: string,
) {
  await request.post(`${apiBase}/api/v1/auth/register`, {
    data: { email, password, firstName, lastName },
  });
  const login = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { email, password },
  });
  expect(login.ok()).toBeTruthy();
  const body = await login.json();
  return body.accessToken as string;
}

async function createOrganization(request: APIRequestContext, token: string, name: string, slug: string) {
  const response = await request.post(`${apiBase}/api/v1/organizations`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, slug },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as { id: string };
}

async function requireServicesApi(
  request: APIRequestContext,
  token: string,
  organizationId: string,
) {
  const response = await request.get(
    `${apiBase}/api/v1/organizations/${organizationId}/services`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (response.status() === 404) {
    test.skip(
      true,
      "Services API returned 404 — run the Phase 4 backend against this database before E2E.",
    );
  }
  expect(response.ok()).toBeTruthy();
}

test.describe("services", () => {
  test("owner can manage service catalog end to end", async ({ page, request }) => {
    const email = `svc-owner-${Date.now()}@example.com`;
    const slug = `svc-org-${Date.now()}`;

    const token = await registerAndLogin(request, email, "Svc", "Owner");
    const org = await createOrganization(request, token, "Service Org", slug);
    await requireServicesApi(request, token, org.id);

    const teamResponse = await request.post(`${apiBase}/api/v1/organizations/${org.id}/teams`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: "Platform", description: "Core" },
    });
    expect(teamResponse.ok()).toBeTruthy();

    await page.goto("/login");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.goto(`/organizations/${org.id}`);
    await page.getByRole("navigation").getByRole("link", { name: "Services" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Services" })).toBeVisible();
    await expect(page.getByText("No services yet.")).toBeVisible();

    await page.locator("#service-name").fill("Payments");
    await page.locator("#service-slug").fill("payments");
    await page.locator("#service-description").fill("Payment processing");
    await page.getByLabel("Owning team (optional)").selectOption({ label: "Platform" });
    await page.getByRole("button", { name: "Create service" }).click();

    await expect(page.getByRole("link", { name: "Payments" })).toBeVisible();
    await expect(page.getByText("Team: Platform")).toBeVisible();

    await page.getByRole("link", { name: "Payments" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Payments" })).toBeVisible();
    await expect(page.getByText("Slug: payments")).toBeVisible();
    await expect(page.getByText("Owning team: Platform")).toBeVisible();

    await page.getByRole("button", { name: "Edit service" }).click();
    await page.locator("#edit-service-name").fill("Payments API");
    await page.locator("#edit-service-slug").fill("payments-api");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Payments API" })).toBeVisible();

    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Delete service" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Services" })).toBeVisible();
    await expect(page.getByText("No services yet.")).toBeVisible();
  });

  test("admin can create services", async ({ page, request }) => {
    const ownerEmail = `svc-admin-owner-${Date.now()}@example.com`;
    const adminEmail = `svc-admin-${Date.now()}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const adminToken = await registerAndLogin(request, adminEmail, "Admin", "User");
    const adminMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })
    ).json();
    const org = await createOrganization(request, ownerToken, "Admin Org", `admin-org-${Date.now()}`);
    await requireServicesApi(request, ownerToken, org.id);
    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: adminMe.id, role: "ADMIN" },
    });

    await page.goto("/login");
    await page.getByLabel("Email").fill(adminEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.goto(`/organizations/${org.id}/services`);
    await page.locator("#service-name").fill("Admin Service");
    await page.locator("#service-slug").fill("admin-service");
    await page.getByRole("button", { name: "Create service" }).click();
    await expect(page.getByRole("link", { name: "Admin Service" })).toBeVisible();
  });

  test("member and viewer are read-only in UI", async ({ page, request }) => {
    const ownerEmail = `svc-owner-ro-${Date.now()}@example.com`;
    const memberEmail = `svc-member-${Date.now()}@example.com`;
    const viewerEmail = `svc-viewer-${Date.now()}@example.com`;
    const orgSlug = `svc-ro-${Date.now()}`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const viewerToken = await registerAndLogin(request, viewerEmail, "Viewer", "User");

    const memberMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${memberToken}` },
      })
    ).json();
    const viewerMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${viewerToken}` },
      })
    ).json();

    const org = await createOrganization(request, ownerToken, "Read Only Org", orgSlug);
    await requireServicesApi(request, ownerToken, org.id);

    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: memberMe.id, role: "MEMBER" },
    });
    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: viewerMe.id, role: "VIEWER" },
    });

    const serviceCreate = await request.post(`${apiBase}/api/v1/organizations/${org.id}/services`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: "Billing", slug: "billing", description: "Billing service" },
    });
    expect(serviceCreate.ok()).toBeTruthy();

    for (const email of [memberEmail, viewerEmail]) {
      await page.goto("/login");
      await page.getByLabel("Email").fill(email);
      await page.getByLabel("Password", { exact: true }).fill(password);
      await page.getByRole("button", { name: "Login" }).click();
      await expect(page).toHaveURL(/\/dashboard$/);

      await page.goto(`/organizations/${org.id}/services`);
      await expect(page.getByRole("heading", { level: 1, name: "Services" })).toBeVisible();
      await expect(page.getByRole("link", { name: "Billing" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Create service" })).toHaveCount(0);

      await page.getByRole("link", { name: "Billing" }).click();
      await expect(page.getByRole("heading", { level: 1, name: "Billing" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Edit service" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Delete service" })).toHaveCount(0);
      await expect(page.getByText(`Service management requires organization OWNER or ADMIN.`)).toBeVisible();

      await page.getByRole("button", { name: "Logout" }).click();
      await expect(page).toHaveURL(/\/login$/);
    }
  });

  test("services stay organization-scoped and outsider gets error", async ({ page, request }) => {
    const ownerEmail = `svc-scope-${Date.now()}@example.com`;
    const outsiderEmail = `svc-outsider-${Date.now()}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Scope", "Owner");
    await registerAndLogin(request, outsiderEmail, "Out", "Side");

    const orgA = await createOrganization(request, ownerToken, "Org A", `org-a-${Date.now()}`);
    const orgB = await createOrganization(request, ownerToken, "Org B", `org-b-${Date.now()}`);
    await requireServicesApi(request, ownerToken, orgA.id);

    const serviceResponse = await request.post(`${apiBase}/api/v1/organizations/${orgA.id}/services`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: "Shared Name", slug: "shared-name" },
    });
    const service = await serviceResponse.json();

    await request.post(`${apiBase}/api/v1/organizations/${orgB.id}/services`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: "Shared Name", slug: "shared-name" },
    });

    await page.goto("/login");
    await page.getByLabel("Email").fill(ownerEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.goto(`/organizations/${orgA.id}/services`);
    await expect(page.getByRole("link", { name: "Shared Name" })).toHaveCount(1);

    await page.goto(`/organizations/${orgB.id}/services`);
    await expect(page.getByRole("link", { name: "Shared Name" })).toHaveCount(1);

    await page.getByRole("button", { name: "Logout" }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.getByLabel("Email").fill(outsiderEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.goto(`/organizations/${orgA.id}/services`);
    await expect(page.locator(".text-destructive").first()).toBeVisible();

    await page.goto(`/organizations/${orgA.id}/services/${service.id}`);
    await expect(page.getByText("You do not have access to this service.")).toBeVisible();
  });
});
