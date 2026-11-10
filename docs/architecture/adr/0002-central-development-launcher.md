# ADR 0002 — Local development startup through a standalone Java orchestrator

- Status: Accepted for development; production deployment excluded
- Baseline: `5c0db30f84059043c9b84fb670ba10ed5f907a72`

## Context
Running a single Spring Boot service from IntelliJ may fail because Compose-injected credentials are absent, `localhost:5432` may point to another PostgreSQL, or gateway and backend instances compete for port 8080. `docker compose up` returning does not prove Java services are ready.

## Decision
Use a dependency-free Java 21 launcher in Maven module `tools/erp-launcher` as the one-click IntelliJ entry point. It validates prerequisites, creates **only missing** local `.env` keys and service databases, launches Compose, checks real password authentication and verifies gateway/backend readiness before reporting success. Configure profile-specific IDE ports and host-loopback PostgreSQL exposure; retain standard Docker settings unchanged.

## Safety boundaries
- No unattended system package installation, secret rotation, credentials disclosure, DB volume deletion or Git mutation.
- If database credentials are inconsistent, fail with a deterministic diagnosis; never reset a persistent volume.
- No implicit action when the IntelliJ project is merely opened; user explicitly starts the Run Configuration.
- No cross-service DB access for business logic; launcher performs only infrastructure setup with the local development DB administrator.

## Alternatives rejected
1. Add startup hooks to each microservice: races, elevated privileges and unpredictable behavior on Kubernetes/production.
2. Have IntelliJ run all seven app mains directly: requires separate credentials/ports and dependency ordering; production remains Docker-based.
3. Automatically overwrite existing `.env` or database role passwords: unsafe against retained PostgreSQL data.

## Consequences
One extra Maven module; seven local-only profile files; loopback-only PostgreSQL port mapping; readiness checks through the Gateway. A future improvement is explicit per-service Docker healthchecks with metrics, graceful orchestration and production-grade secret management.
