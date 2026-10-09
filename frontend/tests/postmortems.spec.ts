import { expect, test } from "@playwright/test";
import {
  clickPublishPostmortem,
  withAcceptedDialog,
  addMember,
  apiBase,
  createIncident,
  createOrganization,
  currentUserId,
  loginThroughUi,
  postmortemSection,
  postmortemStatusLabel,
  registerAndLogin,
  requirePostmortemsApi,
  resolveIncident,
} from "./helpers/e2e-api";

test.describe("postmortems lifecycle", () => {
  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${apiBase}/api/v1/health`);
    if (!health.ok()) {
      test.skip(true, "Backend health check failed — start API on port 8080");
    }
  });

  test("draft create, edit, persist, publish validation, library, unpublish, republish", async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000);
    const stamp = Date.now();
    const email = `pm-life-${stamp}@example.com`;
    const token = await registerAndLogin(request, email, "Life", "Cycle");
    const org = await createOrganization(request, token, "PM Life Org", `pm-life-${stamp}`);
    await requirePostmortemsApi(request, token, org.id);

    const incidentId = await createIncident(request, token, org.id, "Lifecycle incident");
    await resolveIncident(request, token, org.id, incidentId);

    await loginThroughUi(page, email);
    await page.goto(`/organizations/${org.id}/incidents/${incidentId}`);

    const section = postmortemSection(page);
    await expect(section.getByRole("heading", { name: "Postmortem" })).toBeVisible();
    await expect(section.getByText("No postmortem has been written")).toBeVisible();

    await section.getByRole("button", { name: "Create postmortem" }).click();
    await expect(postmortemStatusLabel(section, "Draft")).toBeVisible();
    await expect(section.getByLabel("Summary")).toBeVisible();

    await section.getByRole("button", { name: "Publish" }).click();
    await expect(section.getByText("Summary is required to publish.")).toBeVisible();

    await section.getByLabel("Summary").fill("Initial outage summary");
    await section.getByLabel("Root cause").fill("Bad deploy");
    await section.getByRole("button", { name: "Save changes" }).click();
    await expect(section.getByText("Initial outage summary")).toBeVisible();

    await page.reload();
    await expect(section.getByText("Initial outage summary")).toBeVisible();

    await section.getByRole("button", { name: "Edit" }).click();
    await section.getByLabel("Summary").fill("Updated outage summary");
    await section.getByRole("button", { name: "Save changes" }).click();
    await expect(section.getByRole("button", { name: "Saving…" })).toHaveCount(0);
    await expect(section.getByText("Updated outage summary")).toBeVisible();

    await clickPublishPostmortem(page, section);
    await expect(postmortemStatusLabel(section, "Published")).toBeVisible();

    await page.getByRole("navigation").getByRole("link", { name: "Postmortems" }).click();
    await expect(page.getByRole("heading", { level: 1, name: "Postmortems" })).toBeVisible();
    await expect(page.getByText("Updated outage summary")).toBeVisible();

    await page.getByRole("link", { name: /Postmortem: Lifecycle incident/i }).click();
    await expect(postmortemStatusLabel(section, "Published")).toBeVisible();

    await withAcceptedDialog(page, async () => {
      await section.getByRole("button", { name: "Unpublish" }).click();
    });
    await expect(postmortemStatusLabel(section, "Draft")).toBeVisible();

    await section.getByRole("button", { name: "Edit" }).click();
    await section.getByLabel("Summary").fill("Republished summary text");
    await section.getByRole("button", { name: "Save changes" }).click();

    await clickPublishPostmortem(page, section);
    await expect(postmortemStatusLabel(section, "Published")).toBeVisible();

    await page.getByRole("navigation").getByRole("link", { name: "Postmortems" }).click();
    await expect(page.getByText("Republished summary text")).toBeVisible();
  });

  test("archive, unarchive, delete draft, and library filters", async ({ page, request }) => {
    test.setTimeout(120_000);
    const stamp = Date.now();
    const email = `pm-arch-${stamp}@example.com`;
    const token = await registerAndLogin(request, email, "Arch", "Admin");
    const org = await createOrganization(request, token, "PM Arch Org", `pm-arch-${stamp}`);
    await requirePostmortemsApi(request, token, org.id);

    const publishedIncidentId = await createIncident(request, token, org.id, "Archive target");
    await resolveIncident(request, token, org.id, publishedIncidentId);

    const deleteIncidentId = await createIncident(request, token, org.id, "Delete target");
    await resolveIncident(request, token, org.id, deleteIncidentId);

    await loginThroughUi(page, email);

    await page.goto(`/organizations/${org.id}/incidents/${publishedIncidentId}`);
    const publishedSection = postmortemSection(page);
    await publishedSection.getByRole("button", { name: "Create postmortem" }).click();
    await publishedSection.getByLabel("Summary").fill("Archive me");
    await publishedSection.getByLabel("Root cause").fill("Cause");
    await publishedSection.getByRole("button", { name: "Save changes" }).click();
    await expect(publishedSection.getByRole("button", { name: "Saving…" })).toHaveCount(0);
    await clickPublishPostmortem(page, publishedSection);
    await expect(postmortemStatusLabel(publishedSection, "Published")).toBeVisible();

    await withAcceptedDialog(page, async () => {
      await publishedSection.getByRole("button", { name: "Archive" }).click();
    });
    await expect(postmortemStatusLabel(publishedSection, "Archived")).toBeVisible();

    await page.goto(`/organizations/${org.id}/postmortems`);
    await expect(page.getByText("No published postmortems yet")).toBeVisible();
    await page.getByLabel("Status").selectOption("ARCHIVED");
    await expect(page.getByText("Archive me")).toBeVisible();

    await page.goto(`/organizations/${org.id}/incidents/${publishedIncidentId}`);
    await postmortemSection(page).getByRole("button", { name: "Unarchive" }).click();
    await expect(postmortemStatusLabel(postmortemSection(page), "Draft")).toBeVisible();

    await page.goto(`/organizations/${org.id}/incidents/${deleteIncidentId}`);
    const deleteSection = postmortemSection(page);
    await deleteSection.getByRole("button", { name: "Create postmortem" }).click();
    await withAcceptedDialog(page, async () => {
      await postmortemSection(page).getByRole("button", { name: "Delete draft" }).click();
    });
    await expect(
      postmortemSection(page).getByRole("button", { name: "Create postmortem" }),
    ).toBeVisible();
  });
});

test.describe("postmortems RBAC and isolation", () => {
  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${apiBase}/api/v1/health`);
    if (!health.ok()) {
      test.skip(true, "Backend health check failed — start API on port 8080");
    }
  });

  test("viewer cannot create; member cannot edit another draft; member cannot archive", async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000);
    const stamp = Date.now();
    const ownerEmail = `pm-own-${stamp}@example.com`;
    const memberEmail = `pm-mem-${stamp}@example.com`;
    const viewerEmail = `pm-view-${stamp}@example.com`;
    const otherMemberEmail = `pm-oth-${stamp}@example.com`;

    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "One");
    const viewerToken = await registerAndLogin(request, viewerEmail, "View", "Er");
    const otherToken = await registerAndLogin(request, otherMemberEmail, "Member", "Two");

    const memberId = await currentUserId(request, memberToken);
    const viewerId = await currentUserId(request, viewerToken);
    const otherId = await currentUserId(request, otherToken);

    const org = await createOrganization(request, ownerToken, "PM RBAC Org", `pm-rbac-${stamp}`);
    await requirePostmortemsApi(request, ownerToken, org.id);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");
    await addMember(request, ownerToken, org.id, viewerId, "VIEWER");
    await addMember(request, ownerToken, org.id, otherId, "MEMBER");

    const incidentId = await createIncident(request, memberToken, org.id, "RBAC incident");
    await resolveIncident(request, memberToken, org.id, incidentId);

    await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      { headers: { Authorization: `Bearer ${memberToken}` }, data: {} },
    );
    await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      {
        headers: { Authorization: `Bearer ${memberToken}` },
        data: { summary: "Member draft", rootCause: "Shared" },
      },
    );

    await loginThroughUi(page, viewerEmail);
    await page.goto(`/organizations/${org.id}/incidents/${incidentId}`);
    const viewerSection = postmortemSection(page);
    await expect(viewerSection.getByText("Member draft")).toBeVisible();
    await expect(viewerSection.getByRole("button", { name: "Create postmortem" })).toHaveCount(0);
    await expect(viewerSection.getByRole("button", { name: "Edit" })).toHaveCount(0);

    const viewerCreate = await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      { headers: { Authorization: `Bearer ${viewerToken}` }, data: {} },
    );
    expect(viewerCreate.status()).toBe(403);

    await loginThroughUi(page, otherMemberEmail);
    await page.goto(`/organizations/${org.id}/incidents/${incidentId}`);
    const otherSection = postmortemSection(page);
    await expect(otherSection.getByText("Member draft")).toBeVisible();
    await expect(otherSection.getByRole("button", { name: "Edit" })).toHaveCount(0);
    await expect(otherSection.getByRole("button", { name: "Delete draft" })).toHaveCount(0);

    await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      {
        headers: { Authorization: `Bearer ${memberToken}` },
        data: { summary: "Pub", rootCause: "Pub" },
      },
    );
    await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem/publish`,
      { headers: { Authorization: `Bearer ${memberToken}` } },
    );

    await page.reload();
    await expect(otherSection.getByRole("button", { name: "Archive" })).toHaveCount(0);

    const memberArchive = await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem/archive`,
      { headers: { Authorization: `Bearer ${memberToken}` } },
    );
    expect(memberArchive.status()).toBe(403);
  });

  test("non-member cannot access organization library", async ({ page, request }) => {
    test.setTimeout(90_000);
    const stamp = Date.now();
    const ownerEmail = `pm-iso-${stamp}@example.com`;
    const outsiderEmail = `pm-out-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Iso", "Owner");
    const outsiderToken = await registerAndLogin(request, outsiderEmail, "Out", "Side");
    const org = await createOrganization(request, ownerToken, "Iso Org", `pm-iso-${stamp}`);
    await requirePostmortemsApi(request, ownerToken, org.id);

    const apiList = await request.get(`${apiBase}/api/v1/organizations/${org.id}/postmortems`, {
      headers: { Authorization: `Bearer ${outsiderToken}` },
    });
    expect(apiList.status()).toBe(403);

    await loginThroughUi(page, outsiderEmail);
    await page.goto(`/organizations/${org.id}/postmortems`);
    await expect(page.getByText("Access denied")).toBeVisible();
  });
});
