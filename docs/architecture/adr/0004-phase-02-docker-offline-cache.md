# ADR 0004 — Offline-first Docker tools, host JVM services, persistent PostgreSQL

**Status:** Accepted for development bootstrap (Phase 02)

## Decision

- Separate infrastructure Docker Compose (`infra/compose.infrastructure.yml`) from the legacy
  all-service Compose file. The project name stays `erp-phase1` and named volume stays
  `postgres_data`. Never remove the old file or delete the volume automatically.
- `DockerImageCacheManager` exports the **local real Docker image** as a TAR in
  `.local/docker-images/`, validates SHA-256 and records image ID, platform, source and
  RepoDigest when available. A manifest lock is local and ignored by Git.
- Startup resolution is: compatible Docker image + verified matching cache; otherwise
  verified cache restore; otherwise local image export; otherwise optional trusted image
  pull and export. In `--offline`, network pull is disabled and missing inputs abort.
- Compose uses `pull_policy: never` because the manager owns all pulls/restores.
- PostgreSQL password environment is checked separately from authenticating the
  **persisted role**. DB creation only applies to missing logical databases.
- Flyway remains owned by Java services when they actually run, not by infrastructure.
- Java ERP modules must never be built or run inside this Compose file.

## Tradeoffs

- SHA-256 proves archive/manifest consistency, not independent trustworthiness. Trusted
  distribution of manifest/TAR remains an operator responsibility.
- `postgres:16-alpine` is a patch-floating tag; its resolved image ID/digest must be locked
  after the first export. Multi-platform caches are separate archives.
- Large binary archives cannot practically be committed to the source repository;
  external transfer must include `.local/docker-images/`.
- Multiple installations sharing the **same Docker project name** on one host can collide.
  Deployment-specific names/volumes require a separately designed migration and isolation
  policy in Phase 03, not an unsafe rename during Phase 02.
