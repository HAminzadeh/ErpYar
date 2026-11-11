# Phase 02 — Infrastructure-only Docker, verified offline image cache and PostgreSQL

Base GitHub main: `a6c86fe41f7c6e813cc44022f7797a18be1c0eb3`.
Scope: activities 11–20 of 48.

- Added Docker-only PostgreSQL Compose with original `erp-phase1` project/volume identity.
- Added strict local image manifest, safe archive/hash validation, save/load, platform policy,
  online fallback pull and strict offline mode.
- Added explicit `--infra-only`, `--cache-export`, `--cache-import`, `--offline` options.
- Implemented PostgreSQL startup, real password-authenticated TCP probe, host loopback
  availability check and six missing-database-only provisioning.
- Preserved .env and applied Flyway migrations; no Java service or migration starts in Phase 02.
- Added JUnit simulation tests and independent Python contract tests, documentation and
  IntelliJ Run configurations. No secret, TAR archive or runtime database was included.

**Not delivered here:** selected Java service process supervision (Phase 04), customer
entitlements (Phase 03), or full integration smoke tests (Phase 05).
