import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

export const E2E_PASSWORD = "Password1";
export const apiBase = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function registerAndLogin(
  request: APIRequestContext,
  email: string,
  firstName: string,
  lastName: string,
) {
  const register = await request.post(`${apiBase}/api/v1/auth/register`, {
    data: { email, password: E2E_PASSWORD, firstName, lastName },
  });
  expect(register.ok()).toBeTruthy();
  const login = await request.post(`${apiBase}/api/v1/auth/login`, {
    data: { email, password: E2E_PASSWORD },
  });
  expect(login.ok()).toBeTruthy();
  const body = await login.json();
  return body.accessToken as string;
}

export async function currentUserId(request: APIRequestContext, token: string) {
  const response = await request.get(`${apiBase}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.id as string;
}

export async function createOrganization(
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

export async function addMember(
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

export async function loginThroughUi(page: Page, email: string) {
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
  await page.getByLabel("Password", { exact: true }).fill(E2E_PASSWORD);
  const loginResponse = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/auth/login") && response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Login" }).click();
  const response = await loginResponse;
  expect(response.ok(), `UI login failed: HTTP ${response.status()}`).toBeTruthy();
  await expect(page).toHaveURL(/\/dashboard$/, { timeout: 15_000 });
}

export async function requireOnCallSchedulesApi(
  request: APIRequestContext,
  token: string,
  organizationId: string,
) {
  const response = await request.get(
    `${apiBase}/api/v1/organizations/${organizationId}/on-call-schedules`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok()) {
    test.skip(true, "On-call schedules API is not available (start backend with current code)");
  }
}

export async function createOnCallSchedule(
  request: APIRequestContext,
  token: string,
  organizationId: string,
  body: Record<string, unknown>,
) {
  const response = await request.post(
    `${apiBase}/api/v1/organizations/${organizationId}/on-call-schedules`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: body,
    },
  );
  expect(response.ok()).toBeTruthy();
  const detail = await response.json();
  return detail as { schedule: { id: string; name: string } };
}

export async function requirePostmortemsApi(
  request: APIRequestContext,
  token: string,
  organizationId: string,
) {
  const response = await request.get(
    `${apiBase}/api/v1/organizations/${organizationId}/postmortems`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok()) {
    test.skip(true, "Postmortems API is not available (start backend and database)");
  }
}

export async function createIncident(
  request: APIRequestContext,
  token: string,
  orgId: string,
  title: string,
) {
  const response = await request.post(`${apiBase}/api/v1/organizations/${orgId}/incidents`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { title, severity: "SEV3" },
  });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.id as string;
}

export async function resolveIncident(
  request: APIRequestContext,
  token: string,
  orgId: string,
  incidentId: string,
) {
  await request.patch(`${apiBase}/api/v1/organizations/${orgId}/incidents/${incidentId}`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { status: "ACKNOWLEDGED" },
  });
  const resolved = await request.patch(
    `${apiBase}/api/v1/organizations/${orgId}/incidents/${incidentId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { status: "RESOLVED" },
    },
  );
  expect(resolved.ok()).toBeTruthy();
}

export function postmortemSection(page: Page) {
  return page.locator("section").filter({ has: page.getByRole("heading", { name: "Postmortem" }) });
}

export function postmortemStatusLabel(
  section: ReturnType<typeof postmortemSection>,
  status: "Draft" | "Published" | "Archived",
) {
  return section.getByText(status, { exact: true });
}

/** Run an action that opens a blocking `window.confirm` dialog and accept it. */
export async function withAcceptedDialog(page: Page, action: () => Promise<void>) {
  page.once("dialog", (dialog) => {
    void dialog.accept();
  });
  await action();
}

export async function clickPublishPostmortem(
  page: Page,
  section: ReturnType<typeof postmortemSection>,
) {
  const publishButton = section.getByRole("button", { name: "Publish" });
  await expect(publishButton).toBeEnabled({ timeout: 15_000 });
  await withAcceptedDialog(page, async () => {
    await publishButton.click();
  });
}
