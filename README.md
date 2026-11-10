# ErpYar ERP — Java 21 / Spring Boot 4

Holding-aware, multi-company, modular ERP foundation, currently seven Spring Boot applications (`gateway` and six business services), six logical PostgreSQL databases and a standalone Java 21 startup module (`tools/erp-launcher`). The Phase 1 business foundation is **not production complete**.

## Current developer bootstrap: Smart Bootstrap Phase 01

**Docker is reserved for infrastructure only; Spring Boot services run as host JVM processes in the target architecture.** The source still contains a legacy all-application `compose.yaml` for compatibility, but the new launcher **does not execute it as a full stack**. The infrastructure-only Docker image cache and PostgreSQL lifecycle are Phase 02 tasks; customer module registry is Phase 03; selected Java-process startup is Phase 04.

1. Set IntelliJ Project SDK and Maven Runner JRE to Java 21; reload Maven modules.
2. Run **`ErpYar - Doctor`** to inspect prerequisites without any mutations.
3. Run **`ErpYar - Prepare Environment`** to create/complete local `.env` without rotating existing secrets.
4. Run **`ErpYar - Start All`** to see phase-aware status. In Phase 01 it intentionally exits code **3 / `DEFERRED_NOT_READY`**, rather than claiming ERP applications are up.

See [Launcher README](tools/erp-launcher/README.md) and [Persian Doctor / migration guide](docs/operations/phase-01-doctor-and-recovery.md).

### Developer CLI

```powershell
cd 'D:\ChatGPT_Projects\ErpYar'
mvn -B -ntp verify
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --doctor --json
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --env-only
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --plan
```

Maven CLI is optional for running Doctor from IntelliJ, which can use its bundled Maven. The Doctor checks Java 21, Maven, Docker CLI/Compose v2/Linux Engine, Windows WSL, ports, disk/RAM and local environment. An unavailable Docker Engine is reported without downloading or installing privileged software.

## Modules and API routes (when service startup is implemented)

| Runtime module | Purpose | Route |
|---|---|---|
| gateway | HTTP reverse proxy | `http://localhost:8080` |
| identity | Password login, JWT, users, roles and holding/company grants | `/api/auth`, `/api/users` |
| organization | Holdings, companies, departments | `/api/holdings`, `/api/companies`, `/api/departments` |
| master-data | Company-scoped categories | `/api/master-data/categories` |
| workflow | One-step approval | `/api/workflows/requests` |
| notification | Per-user in-app notifications | `/api/notifications` |
| audit | Best-effort application audit records | `/api/audit/events` |

The `erp-common` library is not an executable service. The future module-selection policy will support Dedicated and Multi-Tenant deployments, with tenant/holding/company permissions independent of running host processes. This policy is not yet operational in Phase 01.

## Repository layout

```text
ErpYar/
├── .run/                         # shared IntelliJ Doctor / Env / Plan / Start configs
├── tools/erp-launcher/           # Java 21 diagnostics/orchestrator
├── libs/erp-common/              # shared JWT, scopes and API utilities
├── services/{gateway,identity,organization,master-data,workflow,notification,audit}/
├── infra/postgres/               # existing DB initialization migrations
├── compose.yaml                  # legacy all-application Compose; not used for new startup
├── docs/architecture/adr/0003-host-java-and-infrastructure-only-docker.md
└── docs/operations/phase-01-doctor-and-recovery.md
```

## Tests

```powershell
mvn -B -ntp verify
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
```

The automated Java runtime contract verifies nested project roots, stable `.env`, read-only Doctor and deferred Plan. A healthy gateway alone is insufficient to validate tenant security and full business workflows. The existing `scripts/smoke_test.py` **writes sample database records** and must not run implicitly or against production data.

## Access and security baseline

- `SYSTEM_ADMIN`, `HOLDING_ADMIN`, `COMPANY_ADMIN`, `STAFF` are existing application-level role concepts. JWT company grants currently govern parts of the MVP; more robust tenant isolation is required before production.
- Grant changes only take effect in new JWTs until token expiration; cross-service consistency must be addressed in later phases.
- The audit HTTP publisher is best effort, not tamper-proof compliance evidence.
- Service authentication, asymmetric key management, MFA, rate limiting, credential rotation, pagination, idempotency and end-to-end tenant authorization need additional work before customer production use.
- Neither the launcher nor the installer deletes persistent database volumes, runs destructive database SQL, resets Git, or commits/pushes on behalf of the developer.

## Existing local-only IDE profile

Service `application-ide.yml` profiles currently connect to PostgreSQL on `127.0.0.1:${ERP_DB_HOST_PORT:-15432}` and use ports `18000..18006`. The earlier `SQLSTATE 28P01` issue requires verifying the real persisted PostgreSQL password; changing `.env` does not rotate the database role. Phase 02 introduces the infrastructure-specific verified recovery procedure.

## Documentation

- [Phase 01 Launcher and Doctor](tools/erp-launcher/README.md)
- [Phase 01 operational checklist](docs/operations/phase-01-doctor-and-recovery.md)
- [ADR 0003: JVM host vs infrastructure Docker](docs/architecture/adr/0003-host-java-and-infrastructure-only-docker.md)
- [API examples](docs/api.http)
- [Baseline quality and technical debt](docs/architecture/technical-debt.md)
