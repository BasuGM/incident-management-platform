import { expect, test } from "@playwright/test";
import {
  createIncident,
  createOrganization,
  loginThroughUi,
  postmortemSection,
  postmortemStatusLabel,
  registerAndLogin,
  requirePostmortemsApi,
  resolveIncident,
  apiBase,
} from "./helpers/e2e-api";

test.describe("postmortems library", () => {
  test.beforeEach(async ({ request }) => {
    const health = await request.get(`${apiBase}/api/v1/health`);
    if (!health.ok()) {
      test.skip(true, "Backend health check failed — start API on port 8080");
    }
  });

  test("published empty state, library listing, and navigation to incident", async ({
    page,
    request,
  }) => {
    test.setTimeout(90_000);
    const stamp = Date.now();
    const email = `pm-lib-${stamp}@example.com`;
    const token = await registerAndLogin(request, email, "Post", "Mortem");
    const org = await createOrganization(request, token, "PM Org", `pm-org-${stamp}`);
    await requirePostmortemsApi(request, token, org.id);

    const incidentId = await createIncident(request, token, org.id, "Library incident");
    await resolveIncident(request, token, org.id, incidentId);

    await loginThroughUi(page, email);
    await page.goto(`/organizations/${org.id}/postmortems`);
    await expect(page.getByRole("heading", { level: 1, name: "Postmortems" })).toBeVisible();
    await expect(page.getByText("No published postmortems yet")).toBeVisible();

    await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      { headers: { Authorization: `Bearer ${token}` }, data: {} },
    );
    await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem`,
      {
        headers: { Authorization: `Bearer ${token}` },
        data: { summary: "Outage summary", rootCause: "Config drift" },
      },
    );
    const publish = await request.post(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}/postmortem/publish`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    expect(publish.ok()).toBeTruthy();

    await page.reload();
    await expect(page.getByText("Outage summary")).toBeVisible();
    await page.getByRole("link", { name: /Postmortem: Library incident/i }).click();
    await expect(postmortemSection(page).getByRole("heading", { name: "Postmortem" })).toBeVisible();
    await expect(postmortemStatusLabel(postmortemSection(page), "Published")).toBeVisible();
  });
});
