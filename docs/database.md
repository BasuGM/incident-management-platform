# Database

## Primary store

PostgreSQL is the primary relational database for the platform.

## Migrations

Schema changes are managed with **Flyway** under:

```text
backend/src/main/resources/db/migration/
```

Migrations:

| Version | Description |
|---------|-------------|
| V1 | Baseline `app` schema |
| V2 | `users` and `refresh_tokens` tables for authentication |
| V3 | Align `refresh_tokens.token_hash` to `VARCHAR(64)` for Hibernate `validate` |
| V4 | `organizations`, `organization_members`, `teams`, `team_members` |
| V5 | `services` (service catalog per organization) |
| V6 | `incidents`, `organization_incident_counters` |
| V7 | `incident_events` (append-only incident audit timeline) |
| V8 | `incident_comments` (soft-deletable incident discussion) |

### Multi-tenancy tables (V4+)

- `app.organizations` — tenant root (`slug` unique)
- `app.organization_members` — user membership + organization role
- `app.teams` — teams scoped to an organization (case-insensitive unique `name` per org in application logic)
- `app.team_members` — users on teams (user must already be an organization member)
- `app.services` — services scoped to an organization; optional `team_id` (`ON DELETE SET NULL`); unique `(organization_id, slug)`; case-insensitive unique `name` per org in application logic
- `app.incidents` — incidents scoped to an organization; optional `service_id` (`ON DELETE RESTRICT`); unique `(organization_id, incident_number)`; reporter and optional commander reference `users` (`ON DELETE RESTRICT`)
- `app.organization_incident_counters` — per-organization atomic counter for incident number allocation (`next_number BIGINT`)
- `app.incident_events` — immutable, append-only events for an incident (`type`, `payload JSONB`, `actor_id`, `organization_id`, `created_at`); FK to `incidents` (`ON DELETE CASCADE`), `organizations` (`ON DELETE CASCADE`), `users` (`ON DELETE RESTRICT`)
- `app.incident_comments` — user-authored plain-text comments (`body` up to 5000 characters, `author_id`, `organization_id`, `incident_id`, `created_at`, `updated_at`, `deleted_at` for soft delete); FK to `incidents` / `organizations` (`ON DELETE CASCADE`), `users` (`ON DELETE RESTRICT`); list ordering `created_at ASC`, `id ASC` via index `(organization_id, incident_id, created_at, id)`

## Domain tables (authentication)

### `app.users`

| Column | Notes |
|--------|-------|
| `id` | UUID primary key |
| `email` | Unique, normalized lowercase |
| `password_hash` | BCrypt hash |
| `first_name`, `last_name` | Profile fields |
| `role` | `ADMIN`, `ENGINEER`, or `VIEWER` |
| `enabled` | Account status |
| `created_at`, `updated_at` | UTC timestamps |

### `app.refresh_tokens`

Stores hashed refresh tokens for rotation, revocation, and multi-device support.

| Column | Notes |
|--------|-------|
| `id` | UUID primary key |
| `user_id` | FK to `users` |
| `token_hash` | SHA-256 hash of refresh token (plaintext never stored) |
| `expires_at` | Expiration timestamp |
| `revoked_at` | Set when token is rotated or logged out |
| `replaced_by_id` | Optional link to replacement token |

## Configuration

Database connectivity is configured through environment variables (see `backend/.env.example`):

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

Never commit real credentials to the repository.
