import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

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

async function createOrganization(
  request: APIRequestContext,
  token: string,
  name: string,
  slug: string,
) {
  const response = await request.post(`${apiBase}/api/v1/organizations`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, slug },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as { id: string };
}

async function addMember(
  request: APIRequestContext,
  ownerToken: string,
  orgId: string,
  userId: string,
  role: string,
) {
  const response = await request.post(`${apiBase}/api/v1/organizations/${orgId}/members`, {
    headers: { Authorization: `Bearer ${ownerToken}` },
    data: { userId, role },
  });
  expect(response.ok()).toBeTruthy();
}

async function requireIncidentsApi(
  request: APIRequestContext,
  token: string,
  organizationId: string,
) {
  const response = await request.get(
    `${apiBase}/api/v1/organizations/${organizationId}/incidents`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok()).toBeTruthy();
}

async function loginThroughUi(page: Page, email: string) {
  await page.goto("/dashboard");
  const logoutButton = page.getByRole("button", { name: "Logout" });
  if (await logoutButton.isVisible()) {
    await logoutButton.click();
    await expect(page).toHaveURL(/\/login$/);
  } else {
    await page.goto("/login");
  }
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Login" })).toBeVisible();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Login" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test.describe("incidents", () => {
  test("navigation, create, list, detail, update, status, viewer, isolation", async ({
    page,
    request,
  }) => {
    test.setTimeout(60_000);
    const stamp = Date.now();
    const ownerEmail = `inc-owner-${stamp}@example.com`;
    const memberEmail = `inc-member-${stamp}@example.com`;
    const viewerEmail = `inc-viewer-${stamp}@example.com`;
    const adminEmail = `inc-admin-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const viewerToken = await registerAndLogin(request, viewerEmail, "Viewer", "User");
    const adminToken = await registerAndLogin(request, adminEmail, "Admin", "User");

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
    const adminMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      })
    ).json();

    const org = await createOrganization(request, ownerToken, "Incident Org", `inc-org-${stamp}`);
    const otherOrg = await createOrganization(request, ownerToken, "Other Org", `inc-other-${stamp}`);
    await requireIncidentsApi(request, ownerToken, org.id);

    await addMember(request, ownerToken, org.id, memberMe.id, "MEMBER");
    await addMember(request, ownerToken, org.id, viewerMe.id, "VIEWER");
    await addMember(request, ownerToken, org.id, adminMe.id, "ADMIN");

    await loginThroughUi(page, memberEmail);

    await page.getByRole("link", { name: "Organizations" }).click();
    await page.getByRole("link", { name: "Open" }).click();
    await page.getByRole("navigation").getByRole("link", { name: "Incidents", exact: true }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Incidents" })).toBeVisible();
    await expect(page.getByText("No incidents yet")).toBeVisible();

    await page.getByRole("link", { name: "Create incident" }).first().click();
    await page.getByLabel("Title").fill("First incident");
    await page.getByLabel("Severity").selectOption("SEV2");
    await page.getByLabel("Description").fill("Initial description");
    await page.getByRole("button", { name: "Create incident" }).click();

    await expect(page.getByText("INC-1")).toBeVisible();
    await expect(page.getByRole("heading", { level: 1, name: "First incident" })).toBeVisible();
    await expect(page.getByText("Initial description")).toBeVisible();

    await page.getByRole("button", { name: "Acknowledge" }).click();
    await expect(page.getByText("ACKNOWLEDGED")).toBeVisible();

    await page.getByRole("button", { name: "Edit details" }).click();
    await page.getByLabel("Title").fill("Updated title");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Updated title" })).toBeVisible();

    await page.getByRole("link", { name: "← Back to incidents" }).click();
    await expect(page.getByRole("cell", { name: "INC-1" })).toBeVisible();
    await expect(page.getByText("SEV2")).toBeVisible();

    await loginThroughUi(page, viewerEmail);

    await page.goto(`/organizations/${org.id}/incidents`);
    await expect(page.getByRole("heading", { level: 1, name: "Incidents" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Create incident" })).toHaveCount(0);
    await page.getByRole("link", { name: "Open" }).click();
    await expect(page.getByRole("button", { name: "Edit details" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Cancel incident" })).toHaveCount(0);

    await loginThroughUi(page, adminEmail);

    await page.goto(`/organizations/${org.id}/incidents`);
    await page.getByRole("link", { name: "Open" }).click();
    await expect(page.getByRole("button", { name: "Cancel incident" })).toBeVisible();
    await page.getByRole("button", { name: "Cancel incident" }).click();
    await expect(page.getByText("CANCELLED")).toBeVisible();
    await expect(page.getByRole("button", { name: "Edit details" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Reopen" })).toHaveCount(0);

    await loginThroughUi(page, ownerEmail);
    await page.goto(`/organizations/${otherOrg.id}/incidents`);
    await expect(page.getByRole("heading", { level: 1, name: "Incidents" })).toBeVisible();
    await expect(page.getByText("No incidents yet")).toBeVisible();
  });

  test("member cannot cancel; pagination", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `inc-page-${stamp}@example.com`;
    const memberEmail = `inc-page-member-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const memberMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${memberToken}` },
      })
    ).json();

    const org = await createOrganization(request, ownerToken, "Page Org", `inc-page-${stamp}`);
    await addMember(request, ownerToken, org.id, memberMe.id, "MEMBER");
    await requireIncidentsApi(request, ownerToken, org.id);

    for (let i = 0; i < 21; i += 1) {
      const response = await request.post(`${apiBase}/api/v1/organizations/${org.id}/incidents`, {
        headers: { Authorization: `Bearer ${memberToken}` },
        data: { title: `Incident ${i}`, severity: "SEV4" },
      });
      expect(response.ok()).toBeTruthy();
    }

    await loginThroughUi(page, memberEmail);

    await page.goto(`/organizations/${org.id}/incidents`);
    await expect(page.getByRole("heading", { level: 1, name: "Incidents" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "INC-21" })).toBeVisible();
    await expect(page.getByText("Page 1 of 2")).toBeVisible();
    await page.getByRole("button", { name: "Next page" }).click();
    await expect(page.getByText("Page 2 of 2")).toBeVisible();

    const incidentId = await getIncidentIdFromList(request, memberToken, org.id, 0);
    await page.goto(`/organizations/${org.id}/incidents/${incidentId}`);
    await expect(page.getByRole("button", { name: "Cancel incident" })).toHaveCount(0);
  });
});

async function getIncidentIdFromList(
  request: APIRequestContext,
  token: string,
  orgId: string,
  page = 0,
) {
  const response = await request.get(
    `${apiBase}/api/v1/organizations/${orgId}/incidents?page=${page}&size=1`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.content[0].id as string;
}
