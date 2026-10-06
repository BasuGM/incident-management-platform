import { expect, test, type APIRequestContext } from "@playwright/test";

const password = "Password1";
const apiBase = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function registerUser(page: import("@playwright/test").Page, email: string) {
  await page.goto("/register");
  await page.getByLabel("First name").fill("Ux");
  await page.getByLabel("Last name").fill("User");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByLabel("Confirm password").fill(password);
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

async function login(page: import("@playwright/test").Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Login" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

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
  return (await login.json()).accessToken as string;
}

test.describe("organization ux", () => {
  test("engineer with no organizations sees dashboard empty state", async ({ page }) => {
    const email = `ux-empty-${Date.now()}@example.com`;
    await registerUser(page, email);
    await expect(page.getByText("No organizations yet")).toBeVisible();
    await expect(page.getByRole("link", { name: "Create organization" })).toBeVisible();
  });

  test("engineer sees organizations on dashboard and can open overview", async ({ page }) => {
    const email = `ux-dash-${Date.now()}@example.com`;
    await registerUser(page, email);
    await page.getByRole("link", { name: "Organizations" }).click();
    await page.getByLabel("Name").fill("Dash Org");
    await page.getByLabel("Slug").fill(`dash-org-${Date.now()}`);
    await page.getByRole("button", { name: "Create organization" }).click();
    await page.getByRole("link", { name: "Dashboard" }).click();
    await expect(page.getByRole("heading", { name: "Dash Org" })).toBeVisible();
    await expect(page.getByText("Role in this organization: OWNER")).toBeVisible();
    await page.getByRole("link", { name: "Open organization" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Dash Org" })).toBeVisible();
    await expect(page.getByRole("navigation").getByRole("link", { name: "Teams" })).toBeVisible();
    await expect(page.getByRole("navigation").getByRole("link", { name: "Services" })).toBeVisible();
    await expect(page.getByRole("navigation").getByRole("link", { name: "Members" })).toBeVisible();
  });

  test("member and viewer see resources without management controls", async ({ page, request }) => {
    const ownerEmail = `ux-owner-${Date.now()}@example.com`;
    const memberEmail = `ux-member-${Date.now()}@example.com`;
    const viewerEmail = `ux-viewer-${Date.now()}@example.com`;
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

    const orgRes = await request.post(`${apiBase}/api/v1/organizations`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: "Ux Org", slug: `ux-org-${Date.now()}` },
    });
    const org = await orgRes.json();

    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: memberMe.id, role: "MEMBER" },
    });
    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: viewerMe.id, role: "VIEWER" },
    });

    for (const email of [memberEmail, viewerEmail]) {
      await login(page, email);
      await page.goto(`/organizations/${org.id}/members`);
      await expect(page.getByRole("heading", { level: 1, name: "Members" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Add member" })).toHaveCount(0);
      await expect(page.getByText("Member management requires organization OWNER or ADMIN.")).toBeVisible();
      await page.goto(`/organizations/${org.id}/teams`);
      await expect(page.getByRole("button", { name: "Create team" })).toHaveCount(0);
      await page.getByRole("button", { name: "Logout" }).click();
      await expect(page).toHaveURL(/\/login$/);
    }
  });

  test("owner sees member management controls", async ({ page, request }) => {
    const ownerEmail = `ux-owner-mgmt-${Date.now()}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await (
      await request.post(`${apiBase}/api/v1/organizations`, {
        headers: { Authorization: `Bearer ${ownerToken}` },
        data: { name: "Mgmt Org", slug: `mgmt-${Date.now()}` },
      })
    ).json();

    await login(page, ownerEmail);
    await page.goto(`/organizations/${org.id}/members`);
    await expect(page.getByRole("button", { name: "Add member" })).toBeVisible();
    await expect(page.getByRole("navigation").getByRole("link", { name: "Settings" })).toBeVisible();
  });
});
