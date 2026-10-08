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

async function currentUserId(request: APIRequestContext, token: string) {
  const response = await request.get(`${apiBase}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.id as string;
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

async function createIncident(
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

async function createCommentViaApi(
  request: APIRequestContext,
  token: string,
  orgId: string,
  incidentId: string,
  body: string,
) {
  const response = await request.post(
    `${apiBase}/api/v1/organizations/${orgId}/incidents/${incidentId}/comments`,
    {
      headers: { Authorization: `Bearer ${token}` },
      data: { body },
    },
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

function commentsHeading(page: Page) {
  return page.getByRole("heading", { level: 2, name: "Comments" });
}

function commentList(page: Page) {
  return page.getByRole("list", { name: "Incident comments" });
}

function commentItem(page: Page, text: string) {
  return commentList(page).getByRole("listitem").filter({ hasText: text });
}

async function openIncident(page: Page, orgId: string, incidentId: string) {
  await page.goto(`/organizations/${orgId}/incidents/${incidentId}`);
  await expect(commentsHeading(page)).toBeVisible();
}

test.describe("incident comments", () => {
  test("owner and member can create a comment", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-owner-${stamp}@example.com`;
    const memberEmail = `cmt-member-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const memberId = await currentUserId(request, memberToken);
    const org = await createOrganization(request, ownerToken, "Comment Org", `cmt-org-${stamp}`);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");
    const incidentId = await createIncident(request, ownerToken, org.id, "Comment incident");

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    const ownerBody = `Owner note ${stamp}`;
    await page.getByLabel("Add a comment").fill(ownerBody);
    await page.getByRole("button", { name: "Post comment" }).click();
    const ownerItem = commentItem(page, ownerBody);
    await expect(ownerItem).toBeVisible();
    await expect(ownerItem).toContainText("Owner User");
    await expect(ownerItem).toContainText(ownerEmail);
    await expect(page.getByText(ownerBody)).toHaveCount(1);

    await loginThroughUi(page, memberEmail);
    await openIncident(page, org.id, incidentId);
    await expect(page.getByLabel("Add a comment")).toBeEnabled();
    const memberBody = `Member note ${stamp}`;
    await page.getByLabel("Add a comment").fill(memberBody);
    await page.getByRole("button", { name: "Post comment" }).click();
    await expect(commentItem(page, memberBody)).toBeVisible();
    await expect(commentItem(page, memberBody)).toContainText("Member User");
    await expect(page.getByText(memberBody)).toHaveCount(1);
  });

  test("viewer is read-only and empty states match role", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-view-owner-${stamp}@example.com`;
    const viewerEmail = `cmt-viewer-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const viewerToken = await registerAndLogin(request, viewerEmail, "Viewer", "User");
    const viewerId = await currentUserId(request, viewerToken);
    const org = await createOrganization(request, ownerToken, "Viewer Org", `cmt-view-${stamp}`);
    await addMember(request, ownerToken, org.id, viewerId, "VIEWER");
    const emptyIncident = await createIncident(request, ownerToken, org.id, "Empty incident");
    const filledIncident = await createIncident(request, ownerToken, org.id, "Filled incident");
    const body = `Visible to viewer ${stamp}`;
    await createCommentViaApi(request, ownerToken, org.id, filledIncident, body);

    await loginThroughUi(page, viewerEmail);
    await openIncident(page, org.id, emptyIncident);
    await expect(page.getByText("Comments will appear here.")).toBeVisible();
    await expect(page.getByLabel("Add a comment")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Post comment" })).toHaveCount(0);

    await openIncident(page, org.id, filledIncident);
    await expect(commentItem(page, body)).toBeVisible();
    await expect(page.getByLabel("Add a comment")).toHaveCount(0);
    await expect(commentItem(page, body).getByRole("button", { name: "Edit" })).toHaveCount(0);
    await expect(commentItem(page, body).getByRole("button", { name: "Delete" })).toHaveCount(0);

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, emptyIncident);
    await expect(page.getByText("No comments yet. Start the conversation about this incident.")).toBeVisible();
  });

  test("author can edit and cancel an edit", async ({ page, request }) => {
    const stamp = Date.now();
    const memberEmail = `cmt-edit-${stamp}@example.com`;
    const memberToken = await registerAndLogin(request, memberEmail, "Editor", "User");
    const org = await createOrganization(request, memberToken, "Edit Org", `cmt-edit-${stamp}`);
    const incidentId = await createIncident(request, memberToken, org.id, "Edit incident");
    const original = `Original ${stamp}`;
    const updated = `Updated ${stamp}`;
    await createCommentViaApi(request, memberToken, org.id, incidentId, original);

    await loginThroughUi(page, memberEmail);
    await openIncident(page, org.id, incidentId);
    const item = commentItem(page, original);
    await item.getByRole("button", { name: "Edit" }).click();
    await page.getByLabel("Edit comment").fill(`${original} discarded`);
    await page.getByRole("button", { name: "Cancel", exact: true }).click();
    await expect(commentItem(page, original)).toBeVisible();
    await expect(page.getByText(`${original} discarded`)).toHaveCount(0);

    await commentItem(page, original).getByRole("button", { name: "Edit" }).click();
    await page.getByLabel("Edit comment").fill(updated);
    await page.getByRole("button", { name: "Save" }).click();
    await expect(commentItem(page, updated)).toBeVisible();
    await expect(page.getByText(original, { exact: true })).toHaveCount(0);
  });

  test("author delete leaves a tombstone", async ({ page, request }) => {
    const stamp = Date.now();
    const memberEmail = `cmt-del-${stamp}@example.com`;
    const memberToken = await registerAndLogin(request, memberEmail, "Deleter", "User");
    const org = await createOrganization(request, memberToken, "Delete Org", `cmt-del-${stamp}`);
    const incidentId = await createIncident(request, memberToken, org.id, "Delete incident");
    const body = `Remove me ${stamp}`;
    await createCommentViaApi(request, memberToken, org.id, incidentId, body);

    await loginThroughUi(page, memberEmail);
    await openIncident(page, org.id, incidentId);
    page.once("dialog", (dialog) => dialog.accept());
    await commentItem(page, body).getByRole("button", { name: "Delete" }).click();
    await expect(page.getByText("Comment deleted")).toBeVisible();
    await expect(page.getByText(body)).toHaveCount(0);
    await expect(commentList(page).getByRole("button", { name: "Edit", exact: true })).toHaveCount(0);
    await expect(commentList(page).getByRole("button", { name: "Delete", exact: true })).toHaveCount(0);
    await expect(commentList(page).getByRole("listitem")).toHaveCount(1);
  });

  test("owner and admin can delete but not edit another user's comment", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-mod-owner-${stamp}@example.com`;
    const adminEmail = `cmt-mod-admin-${stamp}@example.com`;
    const memberEmail = `cmt-mod-member-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const adminToken = await registerAndLogin(request, adminEmail, "Admin", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const adminId = await currentUserId(request, adminToken);
    const memberId = await currentUserId(request, memberToken);
    const org = await createOrganization(request, ownerToken, "Mod Org", `cmt-mod-${stamp}`);
    await addMember(request, ownerToken, org.id, adminId, "ADMIN");
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");
    const incidentId = await createIncident(request, ownerToken, org.id, "Mod incident");
    const ownerTarget = `Owner target ${stamp}`;
    const adminTarget = `Admin target ${stamp}`;
    await createCommentViaApi(request, memberToken, org.id, incidentId, ownerTarget);
    await createCommentViaApi(request, memberToken, org.id, incidentId, adminTarget);

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    const ownerItem = commentItem(page, ownerTarget);
    await expect(ownerItem).toBeVisible();
    await expect(ownerItem.getByRole("button", { name: "Edit" })).toHaveCount(0);
    page.once("dialog", (dialog) => dialog.accept());
    await ownerItem.getByRole("button", { name: "Delete" }).click();
    await expect(page.getByText(ownerTarget)).toHaveCount(0);
    await expect(page.getByText("Comment deleted").first()).toBeVisible();

    await loginThroughUi(page, adminEmail);
    await openIncident(page, org.id, incidentId);
    const adminItem = commentItem(page, adminTarget);
    await expect(adminItem.getByRole("button", { name: "Edit" })).toHaveCount(0);
    page.once("dialog", (dialog) => dialog.accept());
    await adminItem.getByRole("button", { name: "Delete" }).click();
    await expect(page.getByText(adminTarget)).toHaveCount(0);
  });

  test("member cannot moderate another user's comment", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-nomod-owner-${stamp}@example.com`;
    const memberEmail = `cmt-nomod-member-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const memberToken = await registerAndLogin(request, memberEmail, "Member", "User");
    const memberId = await currentUserId(request, memberToken);
    const org = await createOrganization(request, ownerToken, "No Mod Org", `cmt-nomod-${stamp}`);
    await addMember(request, ownerToken, org.id, memberId, "MEMBER");
    const incidentId = await createIncident(request, ownerToken, org.id, "No mod incident");
    const foreign = `Foreign ${stamp}`;
    await createCommentViaApi(request, ownerToken, org.id, incidentId, foreign);

    await loginThroughUi(page, memberEmail);
    await openIncident(page, org.id, incidentId);
    const item = commentItem(page, foreign);
    await expect(item).toBeVisible();
    await expect(item.getByRole("button", { name: "Edit" })).toHaveCount(0);
    await expect(item.getByRole("button", { name: "Delete" })).toHaveCount(0);
  });

  test("terminal incident keeps comments readable and hides writes", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-term-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await createOrganization(request, ownerToken, "Terminal Org", `cmt-term-${stamp}`);
    const incidentId = await createIncident(request, ownerToken, org.id, "Terminal incident");
    const body = `Still readable ${stamp}`;
    await createCommentViaApi(request, ownerToken, org.id, incidentId, body);
    const resolved = await request.patch(
      `${apiBase}/api/v1/organizations/${org.id}/incidents/${incidentId}`,
      {
        headers: { Authorization: `Bearer ${ownerToken}` },
        data: { status: "RESOLVED" },
      },
    );
    expect(resolved.ok()).toBeTruthy();

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    await expect(commentItem(page, body)).toBeVisible();
    await expect(page.getByLabel("Add a comment")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Edit" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Delete" })).toHaveCount(0);
  });

  test("html-like body stays plain text and line breaks remain", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-xss-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await createOrganization(request, ownerToken, "Plain Org", `cmt-xss-${stamp}`);
    const incidentId = await createIncident(request, ownerToken, org.id, "Plain incident");
    let dialogOpened = false;
    page.on("dialog", () => {
      dialogOpened = true;
    });

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    const payload = "<script>alert('xss')</script>";
    await page.getByLabel("Add a comment").fill(payload);
    await page.getByRole("button", { name: "Post comment" }).click();
    await expect(page.getByText(payload)).toBeVisible();
    expect(dialogOpened).toBe(false);
    await expect(commentsHeading(page)).toBeVisible();

    const multiline = `First line ${stamp}\nSecond line ${stamp}`;
    await page.getByLabel("Add a comment").fill(multiline);
    await page.getByRole("button", { name: "Post comment" }).click();
    await expect(page.getByText(`First line ${stamp}`)).toBeVisible();
    await expect(page.getByText(`Second line ${stamp}`)).toBeVisible();
  });

  test("load more keeps chronological order without duplicates", async ({ page, request }) => {
    test.setTimeout(60_000);
    const stamp = Date.now();
    const ownerEmail = `cmt-page-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await createOrganization(request, ownerToken, "Page Org", `cmt-page-${stamp}`);
    const incidentId = await createIncident(request, ownerToken, org.id, "Page incident");
    for (let i = 0; i < 21; i += 1) {
      await createCommentViaApi(request, ownerToken, org.id, incidentId, `Paged ${stamp} ${i}`);
    }

    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    await expect(page.getByText(`Paged ${stamp} 0`)).toBeVisible();
    await expect(page.getByText(`Paged ${stamp} 19`)).toBeVisible();
    await expect(page.getByText(`Paged ${stamp} 20`)).toHaveCount(0);
    const loadMore = page.getByRole("button", { name: "Load more" });
    await expect(loadMore).toBeVisible();
    await loadMore.click();
    await expect(page.getByText(`Paged ${stamp} 20`)).toBeVisible();
    await expect(page.getByText(`Paged ${stamp} 0`)).toHaveCount(1);
    const texts = await commentList(page).getByRole("listitem").allInnerTexts();
    const indexes = texts.map((text) => {
      const match = text.match(/Paged \d+ (\d+)/);
      return match ? Number(match[1]) : -1;
    });
    expect(indexes).toEqual([...indexes].sort((a, b) => a - b));
    expect(new Set(indexes).size).toBe(indexes.length);
  });

  test("comment list error can be retried", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-err-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await createOrganization(request, ownerToken, "Error Org", `cmt-err-${stamp}`);
    const incidentId = await createIncident(request, ownerToken, org.id, "Error incident");
    const body = `Retry me ${stamp}`;
    await createCommentViaApi(request, ownerToken, org.id, incidentId, body);

    let blockComments = true;
    await page.route("**/api/v1/organizations/**/comments**", async (route) => {
      if (route.request().method() === "GET" && blockComments) {
        await route.fulfill({
          status: 500,
          contentType: "application/json",
          body: JSON.stringify({ message: "Comments unavailable" }),
        });
        return;
      }
      await route.continue();
    });

    await loginThroughUi(page, ownerEmail);
    await page.goto(`/organizations/${org.id}/incidents/${incidentId}`);
    await expect(page.getByText("Comments unavailable")).toBeVisible();
    await expect(page.getByRole("button", { name: "Retry" })).toBeVisible();
    blockComments = false;
    await page.getByRole("button", { name: "Retry" }).click();
    await expect(commentItem(page, body)).toBeVisible();
  });

  test("comments stay isolated across organizations and login sessions", async ({ page, request }) => {
    const stamp = Date.now();
    const userAEmail = `cmt-iso-a-${stamp}@example.com`;
    const userBEmail = `cmt-iso-b-${stamp}@example.com`;
    const tokenA = await registerAndLogin(request, userAEmail, "Alice", "One");
    const tokenB = await registerAndLogin(request, userBEmail, "Bob", "Two");
    const orgA = await createOrganization(request, tokenA, "Org A", `cmt-a-${stamp}`);
    const orgB = await createOrganization(request, tokenB, "Org B", `cmt-b-${stamp}`);
    const incidentA = await createIncident(request, tokenA, orgA.id, "Incident A");
    const incidentB = await createIncident(request, tokenB, orgB.id, "Incident B");
    const secret = `Secret A ${stamp}`;
    await createCommentViaApi(request, tokenA, orgA.id, incidentA, secret);

    await loginThroughUi(page, userAEmail);
    await openIncident(page, orgA.id, incidentA);
    await expect(page.getByText(secret)).toBeVisible();

    await loginThroughUi(page, userBEmail);
    await page.goto(`/organizations/${orgA.id}/incidents/${incidentA}`);
    await expect(page.getByText(secret)).toHaveCount(0);

    await openIncident(page, orgB.id, incidentB);
    await expect(page.getByText(secret)).toHaveCount(0);
    await expect(page.getByText("No comments yet. Start the conversation about this incident.")).toBeVisible();
  });

  test("composer stays usable on a narrow viewport", async ({ page, request }) => {
    const stamp = Date.now();
    const ownerEmail = `cmt-mobile-${stamp}@example.com`;
    const ownerToken = await registerAndLogin(request, ownerEmail, "Owner", "User");
    const org = await createOrganization(request, ownerToken, "Mobile Org", `cmt-mob-${stamp}`);
    const incidentId = await createIncident(request, ownerToken, org.id, "Mobile incident");
    const body = `https://example.com/very/long/comment/path/${"segment-".repeat(12)}${stamp}`;

    await page.setViewportSize({ width: 390, height: 844 });
    await loginThroughUi(page, ownerEmail);
    await openIncident(page, org.id, incidentId);
    await page.getByLabel("Add a comment").fill(body);
    await page.getByRole("button", { name: "Post comment" }).click();
    await expect(commentItem(page, stamp.toString())).toBeVisible();
    const overflows = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    );
    expect(overflows).toBe(false);
  });
});
