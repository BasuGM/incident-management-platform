import { expect, test } from "@playwright/test";
import {
  addMember,
  apiBase,
  createOnCallSchedule,
  createOrganization,
  currentUserId,
  loginThroughUi,
  registerAndLogin,
  requireOnCallSchedulesApi,
} from "./helpers/e2e-api";

test.describe("on-call schedules UI", () => {
  test("list, detail, coverage, filter, and navigation", async ({ page, request }) => {
    test.setTimeout(90_000);
    const stamp = Date.now();
    const ownerEmail = `oncall-owner-${stamp}@example.com`;
    const memberEmail = `oncall-member-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const ownerId = await currentUserId(request, ownerToken);
    const memberId = await currentUserId(request, memberToken);

    const org = await createOrganization(request, ownerToken, "On-call Org", `oncall-org-${stamp}`);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");

    await requireOnCallSchedulesApi(request, ownerToken, org.id);

    await createOnCallSchedule(request, ownerToken, org.id, {
      name: "Live rotation",
      timezone: "UTC",
      rotationLengthDays: 1,
      handoffLocalTime: "09:00:00",
      effectiveFrom: "2020-01-01",
      participantUserIds: [ownerId, memberId],
    });

    await createOnCallSchedule(request, ownerToken, org.id, {
      name: "Future start rotation",
      timezone: "UTC",
      rotationLengthDays: 1,
      handoffLocalTime: "09:00:00",
      effectiveFrom: "2035-06-01",
      participantUserIds: [ownerId],
    });

    const archived = await createOnCallSchedule(request, ownerToken, org.id, {
      name: "Archived rotation",
      timezone: "UTC",
      rotationLengthDays: 1,
      handoffLocalTime: "09:00:00",
      effectiveFrom: "2020-01-01",
      participantUserIds: [ownerId],
    });
    const archiveResponse = await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/on-call-schedules/${archived.schedule.id}/archive`,
      { headers: { Authorization: `Bearer ${ownerToken}` } },
    );
    expect(archiveResponse.ok()).toBeTruthy();

    await loginThroughUi(page, memberEmail);
    await page.goto(`/organizations/${org.id}/on-call`);

    await expect(page.getByRole("heading", { name: "On-call", level: 1 })).toBeVisible();
    await expect(page.getByRole("link", { name: "Live rotation" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Future start rotation" })).toBeVisible();
    await expect(page.getByText("Archived rotation")).toHaveCount(0);

    await page.getByLabel("On-call schedule status filter").selectOption("ALL");
    await expect(page.getByRole("link", { name: "Archived rotation" })).toBeVisible();

    await page.getByLabel("On-call schedule status filter").selectOption("ACTIVE");
    await page.getByRole("link", { name: "Live rotation" }).click();

    await expect(page.getByRole("heading", { name: "Live rotation", level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Rotation participants" })).toBeVisible();
    const participantItems = page.locator("section").filter({
      has: page.getByRole("heading", { name: "Rotation participants" }),
    }).locator("ol li");
    await expect(participantItems).toHaveCount(2);
    await expect(participantItems.nth(0)).toContainText("Owner");
    await expect(participantItems.nth(1)).toContainText("Member");

    await expect(page.getByRole("heading", { name: "Coverage" })).toBeVisible();
    await expect(page.getByText(/Times shown in schedule timezone/)).toBeVisible();
    await expect(page.getByRole("heading", { name: "Current primary" })).toBeVisible();

    await page.getByRole("link", { name: "← Back to on-call schedules" }).click();
    await expect(page).toHaveURL(new RegExp(`/organizations/${org.id}/on-call$`));

    await page.getByLabel("On-call schedule status filter").selectOption("ALL");
    await page.getByRole("link", { name: "Future start rotation" }).click();
    await expect(page.getByText("No current coverage yet", { exact: false })).toBeVisible();

    await page.getByRole("link", { name: "← Back to on-call schedules" }).click();
    await expect(page.getByRole("link", { name: "On-call", exact: true })).toBeVisible();
  });
});
