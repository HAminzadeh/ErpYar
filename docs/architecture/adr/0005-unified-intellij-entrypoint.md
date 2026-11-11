# ADR 0005 — Single shared IntelliJ launch entry (2026-11-11)

Status: Accepted — Phase 02.1

## Context

Phases 01 and 02 shipped several `.run/*.run.xml` presets for preflight, environment,
PostgreSQL infrastructure, image import/export and a legacy preview. Developers asked for
one stable Run action and automatic step sequencing. Dedicated/Multi-Tenant module planning
and host-JVM service orchestration do not exist yet (Phases 03/04).

## Decision

- **One shared entry point:** `.run/ErpYar - Run.run.xml` runs
  `ir.erp.launcher.ErpYarLauncher --start` in `erp-launcher`.
- Diagnostic operations remain flags of that same class; no additional committed shared
  IntelliJ Run configuration is created.
- StartupOrchestrator remains the sole lifecycle coordinator, and `InfrastructureManager`
  owns Docker infrastructure; no Java ERP service moves to Docker.
- Until Phase 04, explicit `DEFERRED_NOT_READY` is mandatory after infra boot.
- Keep existing local `.idea` / transient IntelliJ configurations untouched. These are
  personal IDE settings and cannot be safely guessed or globally removed.
- A patch installer may delete only the eight exact tracked shared files after validating
  the expected Git baseline, file hash and absence of local conflicts; preserve backups.

## Consequences

A developer selects one shared IntelliJ configuration. Existing CLI workflows and tests
remain available. The one-button application startup promise is not falsely reported
complete until customer module activation and local JVM process supervision are tested.
Subsequent phases must enhance the same orchestrator, not create parallel entry points.
