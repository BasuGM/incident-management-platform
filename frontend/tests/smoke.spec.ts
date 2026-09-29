import { expect, test } from "@playwright/test";

test("homepage loads", async ({ page }) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", {
      name: "Developer Incident Management Platform",
      level: 1,
    }),
  ).toBeVisible();
});
