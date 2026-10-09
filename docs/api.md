# API conventions

## Base path

All public REST endpoints use:

```text
/api/v1/*
```

## Authentication

Stateless JWT access tokens are sent as:

```text
Authorization: Bearer <access-token>
```

Refresh tokens are issued as HttpOnly cookies on auth endpoints (`Path=/api/v1/auth`) and may also appear in JSON responses for non-browser clients. Browser clients should rely on the cookie for refresh and keep access tokens in memory only.

### `POST /api/v1/auth/register`

Creates a user with the default `ENGINEER` role.

### `POST /api/v1/auth/login`

Authenticates a user and returns access/refresh tokens plus user profile.

### `POST /api/v1/auth/refresh`

Rotates the refresh token and returns a new access token. Accepts the refresh token from the HttpOnly cookie or JSON body.

### `POST /api/v1/auth/logout`

Revokes the refresh token and clears the refresh cookie.

## Users

### `GET /api/v1/users/me`

Returns the authenticated user (requires authentication).

### `GET /api/v1/users`

Lists users (`ADMIN` only).

### `GET /api/v1/users/{userId}`

`ADMIN` for any user; `ENGINEER` / `VIEWER` for their own profile.

### `PATCH /api/v1/users/{userId}`

Updates a user (`ADMIN` only).

## Organizations (tenant-scoped)

Organization roles: `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` (membership role, separate from global `User.role`).

### `GET /api/v1/organizations`

Lists organizations where the current user is a member.

### `POST /api/v1/organizations`

Creates an organization; creator becomes `OWNER`.

### `GET /api/v1/organizations/{organizationId}`

Returns organization details for members only (`403` for non-members).

### `PATCH /api/v1/organizations/{organizationId}`

Updates organization metadata (`OWNER` only).

### `GET|POST /api/v1/organizations/{organizationId}/members`

List/add members (`OWNER`/`ADMIN` for writes).

### `PATCH|DELETE /api/v1/organizations/{organizationId}/members/{userId}`

Update/remove member roles (`OWNER`/`ADMIN` with owner safeguards).

### `GET|POST /api/v1/organizations/{organizationId}/teams`

List/create teams (`OWNER`/`ADMIN` for writes).

### `GET|PATCH|DELETE /api/v1/organizations/{organizationId}/teams/{teamId}`

Read/update/delete a team within the organization (`OWNER`/`ADMIN` for writes).

### `GET|POST /api/v1/organizations/{organizationId}/services`

List/create services in the organization. Any organization member may list; `OWNER`/`ADMIN` may create.

Create body: `name` (required), `slug` (required, organization slug rules, stored lowercase), optional `description`, optional `teamId` (must belong to the same organization).

### `GET|PATCH|DELETE /api/v1/organizations/{organizationId}/services/{serviceId}`

Read/update/delete a service (`OWNER`/`ADMIN` for writes). Cross-organization access returns `403`.

PATCH updates only fields present in the body (same semantics as teams). `teamId` is updated when provided; omitting `teamId` leaves the owning team unchanged.

### `GET|POST /api/v1/organizations/{organizationId}/incidents`

List/create incidents in the organization (authentication required).

**List:** any organization member. Query parameters: `page` (default `0`), `size` (default `20`, max `100`). Results are ordered by `createdAt` descending. Response shape:

```json
{
  "content": [ /* IncidentResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

**Create:** `OWNER`, `ADMIN`, and `MEMBER` may create; `VIEWER` receives `403`. The authenticated user is always the reporter (client cannot set `reporterId`). Create body: `title` (required, max 200), optional `description` (max 10000), `severity` (required: `SEV1`–`SEV4`), optional `serviceId`, optional `commanderId` (must be organization members when set). Returns `201` with `IncidentResponse`. Initial `status` is `OPEN`; `incidentNumber` and `displayId` (`INC-{number}`) are server-assigned.

### `GET|PATCH /api/v1/organizations/{organizationId}/incidents/{incidentId}`

Read/update a single incident. Any organization member may read. Updates require `OWNER`, `ADMIN`, or `MEMBER` (`VIEWER` → `403`). Cross-organization access returns `403`.

PATCH applies only fields present in the JSON body. `serviceId` and `commanderId` may be set to `null` to clear; omitting a field leaves it unchanged. Status transitions and cancellation rules are enforced in the service layer (`409` with codes such as `INVALID_INCIDENT_STATUS_TRANSITION`, `INCIDENT_NOT_EDITABLE`). Cancellation (`status: CANCELLED`) requires `OWNER` or `ADMIN`.

`IncidentResponse` includes: `id`, `organizationId`, `incidentNumber`, `displayId`, `title`, `description`, `severity`, `status`, `serviceId`, `serviceName`, `reporterId`, `reporterEmail`, `reporterFirstName`, `reporterLastName`, `commanderId`, `commanderEmail`, `commanderFirstName`, `commanderLastName`, `createdAt`, `updatedAt`, `acknowledgedAt`, `resolvedAt`, `cancelledAt`.

There is no `DELETE` incident endpoint in Phase 5.

### `GET /api/v1/organizations/{organizationId}/incidents/{incidentId}/events`

Read-only incident timeline (authentication required). Any organization member (`OWNER`, `ADMIN`, `MEMBER`, `VIEWER`) may list events. Cross-organization access returns `403` (same semantics as incident read). Unauthenticated requests return `401`.

Query parameters: `page` (default `0`), `size` (default `20`, max `100`; values above `100` are capped). Events are ordered by `createdAt` descending, then `id` descending (newest first).

Response shape:

```json
{
  "content": [ /* IncidentEventResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`IncidentEventResponse` fields: `id`, `incidentId`, `organizationId`, `actorId`, `actorEmail`, `actorFirstName`, `actorLastName`, `type`, `payload`, `createdAt`.

`type` is one of: `INCIDENT_CREATED`, `STATUS_CHANGED`, `SEVERITY_CHANGED`, `SERVICE_CHANGED`, `COMMANDER_CHANGED`, `TITLE_CHANGED`, `DESCRIPTION_CHANGED`.

`payload` is structured JSON (for example `{"old":"SEV2","new":"SEV1"}` for `SEVERITY_CHANGED`, or creation snapshot fields for `INCIDENT_CREATED`). Events are append-only and created internally by the API when incidents are created or updated; there are no REST endpoints to create, update, or delete events.

### `GET|POST|PATCH|DELETE /api/v1/organizations/{organizationId}/incidents/{incidentId}/comments`

Organization-scoped plain-text comments on an incident (authentication required). Business rules and authorization are enforced in `IncidentCommentService`; paths always include `organizationId` and `incidentId`.

**List (`GET`):** any organization member (`OWNER`, `ADMIN`, `MEMBER`, `VIEWER`). Cross-organization or non-member access returns `403`. Unauthenticated requests return `401`.

Query parameters: `page` (default `0`), `size` (default `20`, max `100`; values above `100` are capped). Comments are ordered by `createdAt` ascending, then `id` ascending (chronological). Soft-deleted comments remain in the list as tombstones (`body` is `""`, `deleted` is `true`, `deletedAt` is set).

Response shape:

```json
{
  "content": [ /* IncidentCommentResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

**Create (`POST`):** `OWNER`, `ADMIN`, and `MEMBER` may create; `VIEWER` receives `403`. The authenticated user is always the author (client cannot set `authorId`). Request body: `{ "body": "..." }` only (`body` required, max 5000 characters after trim; whitespace-only rejected). Returns `201` with `IncidentCommentResponse`. Comment writes are rejected with `409` (`INCIDENT_NOT_EDITABLE`) when the incident is `RESOLVED` or `CANCELLED`.

**Update (`PATCH .../comments/{commentId}`):** author only; `OWNER`/`ADMIN` cannot edit another user's comment (`403`). Request body: `{ "body": "..." }` only (same validation as create). Returns `200` with updated `IncidentCommentResponse`. Updates on soft-deleted comments or terminal incidents return `409` (`COMMENT_NOT_EDITABLE` or `INCIDENT_NOT_EDITABLE`).

**Delete (`DELETE .../comments/{commentId}`):** soft delete (sets `deletedAt`, clears `body`). Author may delete their own comment; `OWNER`/`ADMIN` may delete any comment; other `MEMBER` cannot delete others' comments (`403`). `VIEWER` cannot delete (`403`). Returns `204 No Content`. Terminal incidents reject delete with `409`. Unknown or wrong-scope `commentId` returns `403`.

`IncidentCommentResponse` fields: `id`, `organizationId`, `incidentId`, `authorId`, `authorEmail`, `authorFirstName`, `authorLastName`, `body`, `deleted`, `createdAt`, `updatedAt`, `deletedAt`.

### Postmortems (Phase 8)

Organization-scoped structured post-incident write-ups. Business rules and authorization are enforced in `IncidentPostmortemService`; paths always include `organizationId` and (for singleton routes) `incidentId`. At most **one** postmortem per incident. Writes require incident status **`RESOLVED`** (`CANCELLED` and non-resolved statuses return `409` `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`). No new `IncidentEvent` types.

#### `GET|POST|PATCH|DELETE /api/v1/organizations/{organizationId}/incidents/{incidentId}/postmortem`

**Get (`GET`):** any organization member. Returns **200** + `IncidentPostmortemResponse`. **404** `POSTMORTEM_NOT_FOUND` when no row exists. Cross-organization access returns **403**.

**Create (`POST`):** `OWNER`, `ADMIN`, and `MEMBER` may create; `VIEWER` receives **403**. Optional JSON body (`CreateIncidentPostmortemRequest`): `title` (max 200), optional section fields (`summary`, `impact`, `rootCause`, `resolution`, `lessonsLearned`, `correctiveActions`, each max 10000). Omit body or send `{}` for defaults; server sets `authorId` from the authenticated user and `status` `DRAFT`. Default `title` is `Postmortem: {incident.title}` (truncated to 200). Returns **201**. **409:** `POSTMORTEM_ALREADY_EXISTS`, `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`.

**Update (`PATCH`):** draft only; partial update (only fields present in JSON apply). Same section fields as create. **403** when MEMBER edits another author's draft. **409:** `POSTMORTEM_NOT_EDITABLE`, `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`.

**Delete (`DELETE`):** draft only; **204 No Content**. Author may delete own draft; `OWNER`/`ADMIN` may delete any draft; `VIEWER` **403**. **409** when not draft.

#### Lifecycle (`POST` on singleton subpaths)

All lifecycle routes require authentication, resolved incident, and organization membership (archive/unarchive: `OWNER`/`ADMIN` only). Empty body.

| Path | Effect |
|------|--------|
| `POST .../postmortem/publish` | `DRAFT` → `PUBLISHED`; requires non-empty trimmed `summary` and `rootCause` |
| `POST .../postmortem/unpublish` | `PUBLISHED` → `DRAFT`; clears `publishedAt` / `publishedBy` |
| `POST .../postmortem/archive` | `PUBLISHED` → `ARCHIVED` |
| `POST .../postmortem/unarchive` | `ARCHIVED` → `DRAFT` |

**409 codes:** `POSTMORTEM_PUBLISH_VALIDATION_FAILED`, `INVALID_POSTMORTEM_STATUS_TRANSITION`, `INCIDENT_NOT_ELIGIBLE_FOR_POSTMORTEM`. MEMBER may publish/unpublish only their own postmortem; `OWNER`/`ADMIN` may act on any.

#### `GET /api/v1/organizations/{organizationId}/postmortems`

Paginated organization library. Any organization member; non-members receive **403**. Query: `page` (default `0`), `size` (default `20`, max `100`), `status` (optional: `PUBLISHED` default when omitted, or `DRAFT`, `ARCHIVED`, `ALL`). Invalid `status` → **409** `VALIDATION_ERROR`. Response: `IncidentPostmortemPageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`). `PUBLISHED` / `ARCHIVED` lists ordered by `publishedAt` desc, then `createdAt` desc; `DRAFT` and `ALL` by `createdAt` desc (tie-break `id` desc).

#### `IncidentPostmortemResponse`

`id`, `organizationId`, `incidentId`, `authorId`, `authorEmail`, `authorFirstName`, `authorLastName`, `status` (`DRAFT` | `PUBLISHED` | `ARCHIVED`), `title`, `summary`, `impact`, `rootCause`, `resolution`, `lessonsLearned`, `correctiveActions`, `createdAt`, `updatedAt`, `publishedAt`, `publishedById`, `publishedByEmail`, `publishedByFirstName`, `publishedByLastName`, `archivedAt`.

### `GET|POST /api/v1/teams/{teamId}/members`

List/add team members (`OWNER`/`ADMIN` in parent organization). Team members must already belong to the organization.

### `DELETE /api/v1/teams/{teamId}/members/{userId}`

Remove a team member (`OWNER`/`ADMIN`).

## Health endpoint

### `GET /api/v1/health`

Returns a simple structured response indicating the API process is running.

Example response:

```json
{
  "status": "UP",
  "service": "incident-management-api"
}
```

## Error format

API errors use a consistent JSON envelope (see backend `ApiErrorResponse`):

```json
{
  "timestamp": "2026-09-29T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/example",
  "details": []
}
```

Common auth-related statuses:

| Status | Meaning |
|--------|---------|
| 400 | Validation error |
| 401 | Unauthenticated / invalid credentials / invalid token |
| 403 | Authenticated but forbidden |
| 404 | User not found |
| 409 | Duplicate email |
