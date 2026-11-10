# ErpYar — IntelliJ startup automation (development)

Baseline: `5c0db30f84059043c9b84fb670ba10ed5f907a72` (post-Phase-00 main).

## Added
- Standalone Java 21 bootstrap module `tools/erp-launcher` with a single IntelliJ entry point.
- Local `.env` creation/completion without overwriting existing values; strong random credentials.
- Docker Desktop/Compose check, PostgreSQL startup and password-authentication validation.
- Non-destructive creation of missing logical databases and Docker Compose service startup.
- Readiness polling of Gateway and all six protected backend routes.
- IntelliJ shared Run configurations for full stack and Audit IDE debugging.
- `ide` Spring profile for service-specific localhost DB/ports; `.env` import for direct IDE runs.
- Loopback-only PostgreSQL host port (default 15432); no exposure on external interfaces.
- Unit tests covering .env safety, argument handling and root discovery.
- Architecture ADR and Persian operational runbook.

## Existing behavior preserved
- No changes to existing JPA entities, controllers, Flyway migrations or API contracts.
- Docker services continue using container-local `postgres:5432`, port 8080 internally and gateway 8080 on host.
- No database deletion, credential rotation, Git write, Commit or Push.

## Validation
- Java 21 compilation of launcher source: PASS (OpenJDK 21.0.12.1).
- Offline Python startup contract checks: PASS (4/4).
- Simulated Docker/HTTP orchestration (mock commands and HTTP responses): PASS (one end-to-end dry fixture run; not a real Docker test).
- YAML and XML parse checks: PASS.
- Local Docker Compose and IntelliJ runtime: **NOT RUN in authoring container**; requires user's Windows/Docker environment.
- Maven verify and JUnit: **NOT RUN in authoring container**; Maven not available there.
