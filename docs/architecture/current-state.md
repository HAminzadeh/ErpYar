# ErpYar — current state inventory (Phase 00)

> Evidence date: 2026-11-08. Source: public GitHub `HAminzadeh/ErpYar`, branch `main`, SHA `1c0ba491ec02f1bd380028b50dd232398a64f197`. This is a **source-level audit**, not an end-to-end runtime certification. The changes of this phase are delivered separately and are not yet present in this Git commit.

## Runtime and build topology

- Java 21; root Maven reactor `ir.erp:erp-phase1:1.0.0-SNAPSHOT` (packaging `pom`). Root parent: Spring Boot `4.0.8`; Spring Cloud BOM `2025.1.3`.
- Maven children (8): `libs/erp-common`, `services/gateway`, `services/identity`, `services/organization`, `services/master-data`, `services/workflow`, `services/notification`, `services/audit`.
- 7 deployable services (1 Gateway + 6 business services). Build: single multi-stage `Dockerfile` selects the Maven module via `ARG MODULE`; Java 21 runtime is an unprivileged user.
- Compose: `postgres:16-alpine`, named `postgres_data` persistent volume, six logical databases initialized by `infra/postgres/01-init-databases.sql`. Only Gateway publishes port `8080` (configurable via `GATEWAY_PORT`); services are network-private to Compose.
- API Gateway routing is configured in `services/gateway/src/main/resources/application.yml` using Spring Cloud Gateway WebFlux. Business services use Spring MVC, JPA, Flyway and shared JWT verification.
- Service-to-service business data access: no direct cross-service database access found in inspected configuration. Audit delivery currently uses a synchronous, best-effort HTTP call carrying the end user's JWT, **not** a reliable authenticated message transport.

## Service and persistence inventory

| Module | Responsibility | Gateway paths | DB / Flyway migration | Observed isolation/security |
|---|---|---|---|---|
| gateway | Routing, health/info actuator | `/actuator/health` and routes below | none | Backend apps not host-published |
| identity | Login, user registry, role and holding/company grants | `/api/auth/**`, `/api/users/**` | `identity_db` / `V1__init.sql` | BCrypt; local HS256 JWT with `roles`, `holding_ids`, `company_ids`; 15 min validity |
| organization | Holdings, companies, departments | `/api/holdings/**`, `/api/companies/**`, `/api/departments/**` | `organization_db` / `V1__init.sql` | Controller checks holding/company rights; department parent must be in same company |
| master-data | Categories scoped to company | `/api/master-data/**` | `master_data_db` / `V1__init.sql` | `Access.company()` for list, `companyManager()` for create; unique `(company_id, code)` |
| workflow | Single-step approval/rejection | `/api/workflows/**` | `workflow_db` / `V1__init.sql` | Company manager for decisions; reject self-review; optimistic version column |
| notification | Persisted in-app notification, read flag | `/api/notifications/**` | `notification_db` / `V1__init.sql` | Company manager for create; recipient check for read |
| audit | Application events, company history | `/api/audit/**` | `audit_db` / `V1__init.sql` | Manager-only reads; **JWT-granted company users can create arbitrary events** |

Database ownership is **per service** only. No additional Phase 00 database entity is needed: `ArchitectureInventory`, `ServiceInventory`, `BaselineReport`, `TechnicalDebt` and `GitReference` are evidence documents and CI output, not business domain records. Adding JPA entities or Flyway versions for this documentation would create avoidable operational risk.

## Existing data shape

- `identity`: `app_users` (UUID), `user_roles`, `user_holdings`, `user_companies`. Grant collections are stored as strings referencing IDs controlled by other services; uniqueness and existence of referenced companies/holdings are not enforced across services.
- `organization`: `holdings`, `companies` (FK to holding and business type), `departments` (FK to company and optional parent), unique codes; departments unique per company.
- `master-data`: `categories`, unique `(company_id, code)` and company index.
- `workflow`: `approval_requests` with `status`, `requested_by`, `decided_by`, creation/decision timestamps, and `version` for JPA optimistic locking.
- `notification`: `in_app_notifications` by company and recipient, creation and read timestamps.
- `audit`: `audit_events` with actor/company/action/entity/timestamp, indexed by company/time.

**Tenant model actually implemented:** holding and company context encoded as JWT claims. The company authorization checks happen principally at controller/helper level, not via mandatory PostgreSQL RLS. This is not yet an independent tenant boundary for every future query path.

## Endpoint and operations inventory

- Identity: `POST /api/auth/login`; `GET /api/auth/me`; `GET/POST /api/users`; `PUT /api/users/{id}/access`.
- Organization: `GET/POST /api/holdings`; `GET/POST /api/companies`; `GET/POST /api/departments`.
- Master data: `GET/POST /api/master-data/categories`.
- Workflow: `GET/POST /api/workflows/requests`; `POST /api/workflows/requests/{id}/approve`; `POST /api/workflows/requests/{id}/reject`.
- Notifications: `POST /api/notifications`, `GET /api/notifications/mine`, `POST /api/notifications/{id}/read`.
- Audit: `GET/POST /api/audit/events`.
- Health: `GET /actuator/health` at the gateway. Backend actuator endpoints are configured but not published to the host by Compose.

All existing business endpoints remain unchanged by Phase 00. No new business API/event/schema migration is introduced. Existing `ApiErrors` maps validation, authorization and data conflicts but response shapes are not yet fully uniform.

## Baseline evidence and reproducibility

Inspection included GitHub tree, branch reference, root `pom.xml`, child POMs, Compose, Dockerfile, gateway routing, README, existing scripts, shared authentication/access/error/audit code, relevant controllers/models, and each service's Flyway V1 SQL. Existing Java test file: `libs/erp-common/src/test/java/ir/erp/common/AccessTest.java`. The repository also tracks a legacy `erp-phase1-source.zip`, and a large source-generation script; neither is redistributed in this Phase 00 package.

Checked at the source level: one branch named `main` was returned by the GitHub branches API; current SHA above. Prior phase claims of successful container/build/smoke execution occur in historical commit notes and **were not independently reproduced here**. Local runtime build, Compose validation, live health and integration smoke remain necessary; see `docs/operations/baseline-validation.md`.

Use `python scripts/verify_baseline.py` to generate a structured snapshot at any local checkout; it uses only standard Python libraries and avoids reading `.env` and database contents. It is deliberately limited to structural checks. The exact commands and test statuses are recorded in `docs/roadmap/phase-00.md` and `CHANGELOG_PHASE_00.md`.
