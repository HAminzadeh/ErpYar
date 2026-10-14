# ERP Phase 1 — Java 21 / Spring Boot 4

Executable **development/MVP foundation** for a holding-aware, multi-company, modular ERP. Six business services plus an API gateway, with **one separate PostgreSQL database per business service** (all on a single PostgreSQL server for local development). Data belongs to individual services; no cross-service joins.

> This is a functional phase-one skeleton, **not a production-complete ERP**. Read the security and gaps section below before any real deployment.

## Services

| Module | Purpose | URL via gateway |
|---|---|---|
| gateway | HTTP reverse proxy | `http://localhost:8080` |
| identity | Password login, JWT, users, roles, holding/company grants | `/api/auth`, `/api/users` |
| organization | Holdings, companies, departments | `/api/holdings`, `/api/companies`, `/api/departments` |
| master-data | Per-company categories (extensible) | `/api/master-data/categories` |
| workflow | Approval submissions and one-step review | `/api/workflows/requests` |
| notification | Per-user in-app notifications | `/api/notifications` |
| audit | Append-only audit records (application-level) | `/api/audit/events` |

## Prerequisites

- Docker Engine and Docker Compose v2 (`docker compose`)
- Python 3.9+ for generating `.env`
- Optional: JDK 21 + Maven 3.9+ for local builds

## First run

```bash
cd erp-phase1
python3 scripts/init_env.py  # Windows: py scripts/init_env.py
docker compose up -d --build
docker compose ps
curl http://localhost:8080/actuator/health  # health is served at gateway
```

The first Docker build downloads Maven dependencies and may take time. Ports: only **gateway 8080** is published; PostgreSQL and other services are private to the Compose network.

Find generated bootstrap username/password **locally** in `.env` (never commit or send it). Authenticate:

```bash
curl -sS http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"REPLACE_WITH_ENV_PASSWORD"}'
```

Copy `accessToken` from JSON and use `Authorization: Bearer TOKEN`. Tokens expire in 15 minutes. `docs/api.http` contains runnable requests for IntelliJ REST client / VS Code REST Client.

Example flow: login -> create a holding -> create a company with `businessType` = `SERVICE`/`TRADE`/`MANUFACTURING`/`INVESTMENT`/`OTHER` -> add departments -> create other users -> grant company IDs -> create categories, workflows and notifications.

## Structure

```
erp-phase1/
├── compose.yaml
├── Dockerfile
├── pom.xml
├── docs/api.http
├── infra/postgres/01-init-databases.sql
├── libs/erp-common/                 # JWT verification, company-scoping, API errors, audit client
├── services/
│   ├── gateway/
│   ├── identity/
│   ├── organization/
│   ├── master-data/
│   ├── workflow/
│   ├── notification/
│   └── audit/
└── scripts/init_env.py
```

## Development build & test

```bash
mvn -B verify       # host must have JDK 21 / Maven
# or with Docker, without locally installed Maven:
docker run --rm -v "${PWD}:/src" -w /src maven:3.9-eclipse-temurin-21 mvn -B verify
```

On Windows PowerShell use `-v "${PWD}:/src"` in the Docker command. To rebuild services: `docker compose up -d --build`. To stop without erasing data: `docker compose down`. **Destructive** wipe, including all databases: `docker compose down -v`.

## Smoke test (creates local demo records)

After all services are healthy: `python3 scripts/smoke_test.py`. It tests login, holding/company creation, department, master data, workflow four-eyes rule, audit and access-grant endpoints. The test writes records into the local DB.

## Access model

- `SYSTEM_ADMIN`: system-wide administration.
- `HOLDING_ADMIN`: can manage its granted holding in organization-service; **does not automatically have cross-service company access**.
- `COMPANY_ADMIN`: can manage assigned companies.
- `STAFF`: can read/work in explicitly granted companies but cannot perform manager-only operations.
- Company-specific operations check JWT `company_ids` (except SYSTEM_ADMIN).
- Every authenticated request must carry its own signed JWT. Client-supplied tenant headers are **never trusted** for authorization.
- `identity` service is the authority for grants; access changes propagate to new tokens. Previously issued tokens remain valid until expiration (maximum 15 minutes), without revocation.
- Holding and company ID assignments are administrator managed; **cross-service existence checks for granted IDs are not yet implemented**.

## Security / functional gaps before production

1. **Replace local HMAC JWT and simple password login** with a managed authorization server, asymmetric keys, MFA, session/token revocation, self-service password reset, login rate limiting, and rotation.
2. **Use service authentication + reliable transactional outbox/event broker** for audit. The current HTTP audit publisher is best-effort and may lose events on failures; audit API permits a JWT-authenticated user with a company grant to submit arbitrary events. It is an MVP event log, **not tamper-proof compliance evidence**.
3. Add database-level or query-enforced tenancy/RLS across all repositories, not merely controller checks; test each endpoint for IDOR and tenant leakage.
4. Add schema/DTO separation instead of exposing JPA entities; pagination, request-size rules, idempotency, validation, conflict handling, tests, tracing and dashboards.
5. Add a configurable multi-step BPM engine, email/SMS delivery, full organization hierarchy, granular permissions and a generic master-data registry in subsequent iterations of phase 1.
6. Protect backups, secrets, database accounts, TLS and networks; use independent DB users, audited migrations and deployment policy for production.

## Tech references

- Spring Boot 4.0.8: https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/
- Spring Cloud 2025.1.3: https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/
