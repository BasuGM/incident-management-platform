import { expect, test } from "@playwright/test";
import {
  addMember,
  apiBase,
  createOrganization,
  currentUserId,
  loginThroughUi,
  registerAndLogin,
  requireOnCallSchedulesApi,
} from "./helpers/e2e-api";

async function createTeamInOrg(
  request: Parameters<typeof createOrganization>[0],
  token: string,
  orgId: string,
  name: string,
) {
  const response = await request.post(`${apiBase}/api/v1/organizations/${orgId}/teams`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as { id: string };
}

test.describe("on-call schedule management", () => {
  test("admin create, reorder, edit scope, archive, reactivate; member read-only", async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000);
    const stamp = Date.now();
    const ownerEmail = `oc-mgmt-owner-${stamp}@example.com`;
    const memberEmail = `oc-mgmt-member-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const memberId = await currentUserId(request, memberToken);

    const org = await createOrganization(request, ownerToken, "Mgmt Org", `oc-mgmt-${stamp}`);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");

    await requireOnCallSchedulesApi(request, ownerToken, org.id);

    const team = await createTeamInOrg(request, ownerToken, org.id, "Platform");
    await request.post(`${apiBase}/api/v1/teams/${team.id}/members`, {
      headers: { Authorization: `Bearer ${ownerToken}` },
      data: { userId: memberId },
    });

    await loginThroughUi(page, ownerEmail);
    await page.goto(`/organizations/${org.id}/on-call/new`);

    await page.getByLabel("Schedule name").fill("Daily org rotation");
    await page.getByLabel("Rotation cadence").selectOption("1");
    await page.getByLabel("Timezone (IANA)").fill("UTC");
    await page.getByLabel("Handoff time (local)").fill("10:00");
    await page.getByLabel("Effective from").fill("2020-01-01");

    await page.getByLabel("Add participant").selectOption({ label: "Owner User" });
    await page.getByRole("button", { name: "Add" }).click();
    await page.getByLabel("Add participant").selectOption({ label: "Member User" });
    await page.getByRole("button", { name: "Add" }).click();
    await page.getByRole("button", { name: "Move Owner User down" }).click();

    await page.getByRole("button", { name: "Create schedule" }).click();
    await expect(page.getByRole("heading", { name: "Daily org rotation", level: 1 })).toBeVisible();
    const participantItems = page.locator("section").filter({
      has: page.getByRole("heading", { name: "Rotation participants" }),
    }).locator("ol li");
    await expect(participantItems.nth(0)).toContainText("Member");
    await expect(participantItems.nth(1)).toContainText("Owner");

    await page.getByRole("link", { name: "Edit schedule" }).click();
    await page.getByLabel("Schedule name").fill("Daily org rotation updated");
    await page.getByRole("radio", { name: "Team" }).check();
    await page.getByLabel("Team scope").selectOption({ label: "Platform" });
    await page.getByLabel("Add participant").selectOption({ label: "Member User" });
    await page.getByRole("button", { name: "Add" }).click();
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByRole("heading", { name: "Daily org rotation updated", level: 1 })).toBeVisible();
    await expect(page.getByText("Team · Platform")).toBeVisible();

    page.once("dialog", (dialog) => void dialog.accept());
    await page.getByRole("button", { name: "Archive" }).click();
    await expect(page.getByText("Archived", { exact: true })).toBeVisible();
    await expect(page.getByText(/live coverage is not computed for archived schedules/i)).toBeVisible();

    await page.getByRole("button", { name: "Reactivate" }).click();
    await expect(page.getByText("Active", { exact: true })).toBeVisible();

    await loginThroughUi(page, memberEmail);
    await page.goto(`/organizations/${org.id}/on-call`);
    await expect(page.getByRole("link", { name: "Daily org rotation updated" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Create schedule" })).toHaveCount(0);

    await page.goto(`/organizations/${org.id}/on-call/new`);
    await expect(page.getByText(/do not have permission/i)).toBeVisible();
  });
});
