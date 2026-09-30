# Developer Incident Management Platform

Foundation monorepo for a developer-focused incident management product. **Phase 2 (authentication, users, RBAC)** is implemented. Incident and organization features are not implemented yet.

## Architecture

```text
Next.js  →  Spring Boot REST API  →  PostgreSQL
Spring Boot  →  Redis
```

See [docs/architecture.md](docs/architecture.md) for details.

## Technology stack

| Area | Technologies |
|------|----------------|
| Frontend | Next.js, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, Playwright |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, Actuator |
| Data | PostgreSQL, Redis |
| Tooling | Gradle (wrapper), ESLint, Prettier, Docker Compose |

## Prerequisites

- Node.js 20+ and npm
- Java 21 (JDK)
- Docker Desktop or Docker Engine (for PostgreSQL, Redis, and Testcontainers)

## Installation

```bash
cd incident-management-platform/frontend
npm install

cd ../backend
./gradlew --version
```

## Environment configuration

1. Copy compose environment template:

```bash
cd incident-management-platform
cp .env.example .env
# Edit .env and set DB_PASSWORD
```

2. Copy app templates:

```bash
cp frontend/.env.example frontend/.env.local
cp backend/.env.example backend/.env
```

Export backend variables before running Spring Boot (example):

```bash
set -a && source backend/.env && set +a
```

## Start PostgreSQL and Redis

```bash
cd incident-management-platform
docker compose up -d
```

Default ports:

- PostgreSQL: `5432`
- Redis: `6379`

## Start backend

```bash
cd incident-management-platform/backend
set -a && source .env && set +a   # macOS/Linux
./gradlew bootRun
```

Health check:

```bash
curl http://localhost:8080/api/v1/health
```

Actuator health (infrastructure): `http://localhost:8080/actuator/health`

## Start frontend

```bash
cd incident-management-platform/frontend
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). Register a user at `/register` or sign in at `/login`.

Auth APIs live under `/api/v1/auth/*` on the backend (`http://localhost:8080`). Set `JWT_SECRET` in `backend/.env` before starting the API.

## Run backend tests

Requires Docker (Testcontainers):

```bash
cd incident-management-platform/backend
./gradlew test
```

## Run frontend checks

```bash
cd incident-management-platform/frontend
npm run typecheck
npm run lint
npm run build
npm run test:e2e
```

## Project structure

```text
incident-management-platform/
├── frontend/          # Next.js app
├── backend/           # Spring Boot API
├── docker/            # Future container assets
├── docs/              # Product and engineering docs
├── docker-compose.yml
└── .cursor/rules/     # Cursor agent guidance
```

## Documentation

- [Requirements](docs/requirements.md)
- [Development plan](docs/development-plan.md)
- [API conventions](docs/api.md)
- [Database](docs/database.md)
- [Decisions](docs/decisions.md)

## Suggested next step

Implement **Phase 3: Organizations and teams** (tenancy, membership, and admin flows).
