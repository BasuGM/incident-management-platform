const SERVICE_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export const SERVICE_SLUG_VALIDATION_MESSAGE =
  "Slug must use lowercase letters, numbers, and hyphens only (e.g. payments or payments-api).";

export function isValidServiceSlug(slug: string): boolean {
  const trimmed = slug.trim();
  return trimmed.length > 0 && trimmed.length <= 100 && SERVICE_SLUG_PATTERN.test(trimmed);
}
