# Database

## Primary store

PostgreSQL is the primary relational database for the platform.

## Migrations

Schema changes are managed with **Flyway** under:

```text
backend/src/main/resources/db/migration/
```

The foundation phase includes migration infrastructure only (baseline migration). Domain tables will be introduced in later phases.

## Configuration

Database connectivity is configured through environment variables (see `backend/.env.example`):

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

Never commit real credentials to the repository.
