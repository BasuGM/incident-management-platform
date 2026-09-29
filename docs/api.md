# API conventions

## Base path

All public REST endpoints use:

```text
/api/v1/*
```

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

Authentication and domain endpoints are not implemented in the foundation phase.
