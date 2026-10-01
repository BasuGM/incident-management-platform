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
    data: {
      email,
      password,
      firstName,
      lastName,
    },
  });
  const login = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { email, password },
  });
  expect(login.ok()).toBeTruthy();
  const body = await login.json();
  return body.accessToken as string;
}

test.describe("organizations", () => {
  test("login, create organization, selector, team, and member", async ({ page, request }) => {
    const email = `org-user-${Date.now()}@example.com`;

    await page.goto("/register");
    await page.getByLabel("First name").fill("Org");
    await page.getByLabel("Last name").fill("User");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByLabel("Confirm password").fill(password);
    await page.getByRole("button", { name: "Register" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.getByRole("link", { name: "Organizations" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Organizations" })).toBeVisible();
    const slug = `acme-${Date.now()}`;
    await page.getByLabel("Name").fill("Acme Engineering");
    await page.getByLabel("Slug").fill(slug);
    await page.getByRole("button", { name: "Create organization" }).click();

    await expect(page.getByRole("link", { name: "Acme Engineering" })).toBeVisible();
    await expect(page.getByText("OWNER")).toBeVisible();
    await expect(page.locator("select")).toContainText("Acme Engineering");

    await page.getByRole("link", { name: "Acme Engineering" }).click();
    await page.getByLabel("Team name").fill("Payments");
    await page.getByLabel("Description").fill("Payment systems");
    await page.getByRole("button", { name: "Create team" }).click();
    await expect(page.getByRole("link", { name: "Payments" })).toBeVisible();

    const loginResponse = await request.post(`${apiBase}/api/v1/auth/login`, {
      data: { email, password },
    });
    const { accessToken: token } = await loginResponse.json();
    const meResponse = await request.get(`${apiBase}/api/v1/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const me = await meResponse.json();

    await page.getByRole("link", { name: "Payments" }).click();
    await page.getByLabel("User ID").fill(me.id);
    await page.getByRole("button", { name: "Add team member" }).click();
    await expect(page.getByText(me.email)).toBeVisible();
  });

  test("viewer cannot manage teams in UI", async ({ page, request }) => {
    const ownerEmail = `owner-${Date.now()}@example.com`;
    const viewerEmail = `viewer-${Date.now()}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const viewerToken = await registerAndLogin(request, viewerEmail, "Viewer", "User");

    const viewerMe = await (
      await request.get(`${apiBase}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${viewerToken}` },
      })
    ).json();

    const orgResponse = await request.post(`${apiBase}/api/v1/organizations`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { name: "Viewer Org", slug: `viewer-org-${Date.now()}` },
    });
    const org = await orgResponse.json();

    await request.post(`${apiBase}/api/v1/organizations/${org.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: viewerMe.id, role: "VIEWER" },
    });

    await page.goto("/login");
    await page.getByLabel("Email").fill(viewerEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Login" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.getByRole("link", { name: "Organizations" }).click();
    await page.getByRole("link", { name: "Viewer Org" }).click();
    await expect(page.getByRole("button", { name: "Create team" })).toHaveCount(0);
    await expect(
      page.getByText("Team membership management requires organization OWNER or ADMIN."),
    ).toHaveCount(0);
  });

  test("organization switching", async ({ page }) => {
    const email = `switch-${Date.now()}@example.com`;

    await page.goto("/register");
    await page.getByLabel("First name").fill("Switch");
    await page.getByLabel("Last name").fill("User");
    await page.getByLabel("Email").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByLabel("Confirm password").fill(password);
    await page.getByRole("button", { name: "Register" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await page.getByRole("link", { name: "Organizations" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Organizations" })).toBeVisible();
    await page.getByLabel("Name").fill("Alpha Corp");
    await page.getByLabel("Slug").fill(`alpha-${Date.now()}`);
    await page.getByRole("button", { name: "Create organization" }).click();
    await page.getByLabel("Name").fill("Beta Corp");
    await page.getByLabel("Slug").fill(`beta-${Date.now()}`);
    await page.getByRole("button", { name: "Create organization" }).click();

    const selector = page.locator("select");
    await expect(selector).toContainText("Alpha Corp");
    await expect(selector).toContainText("Beta Corp");

    await selector.selectOption({ label: "Beta Corp" });
    const betaValue = await selector.locator('option:has-text("Beta Corp")').getAttribute("value");
    expect(betaValue).toBeTruthy();
    await expect(selector).toHaveValue(betaValue!);
  });
});
