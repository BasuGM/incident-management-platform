import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { ApiError, createApiClient } from "@/lib/api/client";
import {
  incidentPostmortemPath,
  organizationPostmortemsPath,
  publishIncidentPostmortem,
  serializeUpdateIncidentPostmortemBody,
} from "@/lib/api/incident-postmortems";

const postMock = vi.fn();

vi.mock("@/lib/api/authenticated-client", () => ({
  getAuthenticatedClient: () => ({
    post: postMock,
    get: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    request: vi.fn(),
  }),
}));

const ORG = "11111111-1111-1111-1111-111111111111";
const INC = "22222222-2222-2222-2222-222222222222";

describe("incident-postmortems paths", () => {
  it("builds singleton path", () => {
    expect(incidentPostmortemPath(ORG, INC)).toBe(
      `/api/v1/organizations/${ORG}/incidents/${INC}/postmortem`,
    );
  });

  it("builds organization list with defaults and omits status", () => {
    expect(organizationPostmortemsPath(ORG)).toBe(
      `/api/v1/organizations/${ORG}/postmortems?page=0&size=20`,
    );
  });

  it("serializes status filters", () => {
    expect(organizationPostmortemsPath(ORG, { status: "DRAFT" })).toContain("status=DRAFT");
    expect(organizationPostmortemsPath(ORG, { status: "ALL" })).toContain("status=ALL");
  });

  it("caps page size at 100", () => {
    expect(organizationPostmortemsPath(ORG, { size: 500 })).toContain("size=100");
  });
});

describe("serializeUpdateIncidentPostmortemBody", () => {
  it("omits unset fields", () => {
    expect(serializeUpdateIncidentPostmortemBody({ summary: "x" })).toEqual({ summary: "x" });
  });

  it("preserves explicit null", () => {
    expect(serializeUpdateIncidentPostmortemBody({ summary: null })).toEqual({ summary: null });
  });
});

describe("incident-postmortems HTTP client", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("DELETE handles 204 without JSON", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
    });

    const client = createApiClient({ baseUrl: "http://api.test" });
    const result = await client.delete<void>(incidentPostmortemPath(ORG, INC));
    expect(result).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      `http://api.test${incidentPostmortemPath(ORG, INC)}`,
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("PATCH sends body including null", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ id: "pm-1", status: "DRAFT" }),
    });

    const client = createApiClient({ baseUrl: "http://api.test" });
    await client.patch(incidentPostmortemPath(ORG, INC), { summary: null });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe("PATCH");
    expect(init.body).toBe(JSON.stringify({ summary: null }));
  });

  it("throws ApiError with status for 404", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => ({
        status: 404,
        error: "POSTMORTEM_NOT_FOUND",
        message: "Postmortem not found",
        path: "/api/v1/x",
        details: [],
        timestamp: "2026-01-01T00:00:00Z",
      }),
    });

    const client = createApiClient({ baseUrl: "http://api.test" });
    const error = await client
      .get(incidentPostmortemPath(ORG, INC))
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 404 });
    expect((error as ApiError).body).toMatchObject({ error: "POSTMORTEM_NOT_FOUND" });
  });

  it("throws ApiError for 409 conflict", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => ({
        status: 409,
        error: "POSTMORTEM_PUBLISH_VALIDATION_FAILED",
        message: "Publish validation failed",
        path: "/api/v1/x",
        details: [],
        timestamp: "2026-01-01T00:00:00Z",
      }),
    });

    const client = createApiClient({ baseUrl: "http://api.test" });
    await expect(
      client.post(`${incidentPostmortemPath(ORG, INC)}/publish`),
    ).rejects.toMatchObject({ status: 409 });
  });
});

describe("publishIncidentPostmortem", () => {
  beforeEach(() => {
    postMock.mockReset();
    postMock.mockResolvedValue({ id: "pm", status: "PUBLISHED" });
  });

  it("POSTs to publish subpath", async () => {
    await publishIncidentPostmortem(ORG, INC);
    expect(postMock).toHaveBeenCalledWith(`${incidentPostmortemPath(ORG, INC)}/publish`);
  });
});
