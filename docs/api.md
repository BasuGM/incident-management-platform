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
