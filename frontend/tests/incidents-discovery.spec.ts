import { expect, test, type APIRequestContext } from "@playwright/test";
import {
  addMember,
  apiBase,
  createOrganization,
  loginThroughUi,
  registerAndLogin,
} from "./helpers/e2e-api";

async function currentUserId(request: APIRequestContext, token: string) {
  const response = await request.get(`${apiBase}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()).id as string;
}

async function createIncident(
  request: APIRequestContext,
  token: string,
  orgId: string,
  body: Record<string, unknown>,
) {
  const response = await request.post(`${apiBase}/api/v1/organizations/${orgId}/incidents`, {
    headers: { Authorization: `Bearer ${token}` },
    data: body,
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as { id: string; displayId: string };
}

test.describe("incident discovery", () => {
  test("default active view, filters, search, pagination, reload", async ({ page, request }) => {
    test.setTimeout(90_000);
    const stamp = Date.now();
    const ownerEmail = `disc-owner-${stamp}@example.com`;
    const memberEmail = `disc-member-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const memberId = await currentUserId(request, memberToken);

    const org = await createOrganization(request, ownerToken, "Discovery Org", `disc-org-${stamp}`);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");

    const open = await createIncident(request, memberToken, org.id, {
      title: "Checkout outage",
      severity: "SEV1",
      commanderId: memberId,
    });
    const resolved = await createIncident(request, memberToken, org.id, {
      title: "Old resolved",
      severity: "SEV4",
    });
    await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${resolved.id}`,
      {
        headers: { Authorization: `Bearer ${memberToken}` },
        data: { status: "ACKNOWLEDGED" },
      },
    );
    await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${resolved.id}`,
      {
        headers: { Authorization: `Bearer ${memberToken}` },
        data: { status: "RESOLVED" },
      },
    );

    await loginThroughUi(page, memberEmail);
    await page.goto(`/organizations/${org.id}/incidents`);

    await expect(page).toHaveURL(/active=true/);
    await expect(page.getByRole("cell", { name: open.displayId })).toBeVisible();
    await expect(page.getByText("Old resolved")).toHaveCount(0);

    await page.getByLabel("View").selectOption("all");
    await expect(page).toHaveURL(/active=all/);
    await expect(page.getByText("Old resolved")).toBeVisible();

    await page.getByLabel("View").selectOption("active");
    await page.getByLabel("Severity").selectOption("SEV1");
    await expect(page.getByRole("cell", { name: open.displayId })).toBeVisible();

    await page.getByLabel("Commander").selectOption("My incidents");
    await expect(page).toHaveURL(/mine=true/);
    await expect(page.getByRole("cell", { name: open.displayId })).toBeVisible();

    await page.getByLabel("Search").fill("checkout");
    await expect(page).toHaveURL(/search=checkout/);
    await expect(page.getByText("Checkout outage")).toBeVisible();

    await page.getByLabel("Search").fill(open.displayId);
    await expect(page.getByRole("cell", { name: open.displayId })).toBeVisible();

    await page.getByRole("button", { name: "Clear filters" }).click();
    await expect(page).toHaveURL(/active=true/);

    for (let i = 0; i < 21; i += 1) {
      await createIncident(request, memberToken, org.id, {
        title: `Bulk ${i}`,
        severity: "SEV3",
      });
    }

    await page.goto(`/organizations/${org.id}/incidents?active=true`);
    await expect(page.getByText("Page 1 of")).toBeVisible();
    await page.getByRole("button", { name: "Next page" }).click();
    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByText("Page 2 of")).toBeVisible();

    const filteredUrl = page.url();
    await page.reload();
    await expect(page).toHaveURL(filteredUrl);

    await page.getByLabel("Severity").selectOption("SEV1");
    await page.getByLabel("Search").fill("no-such-title-xyz");
    await expect(page.getByRole("heading", { name: "No matching incidents" })).toBeVisible();
    await page.getByRole("button", { name: "Clear filters" }).nth(1).click();
    await expect(page.getByRole("heading", { name: "No matching incidents" })).toHaveCount(0);
  });
});
