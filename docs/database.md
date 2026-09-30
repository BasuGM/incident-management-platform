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
