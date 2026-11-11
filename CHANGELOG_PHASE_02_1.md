# Phase 02.1 — Unified Entry Point

Base Git HEAD: `febca97f9826079f9e38c9524d77c8ab8c5b633b`

- Consolidated eight shared `.run` presets into one `ErpYar - Run` configuration.
- Preserved the existing `ir.erp.launcher.ErpYarLauncher` Java 21 main entry.
- Kept doctor, environment, offline cache, image export/import and infra-only as CLI modes.
- Kept Docker limited to PostgreSQL infrastructure and host JVM service startup deferred.
- Updated user-facing README, operations docs, launcher help and regression contracts.
- Added Phase 02.1 dedicated Java/Python tests.
- Added architecture ADR describing the single-entry decision and local IDE setting limits.

**Runtime boundary:** With Phases 03/04 not implemented, launching the project still
reports `DEFERRED_NOT_READY` (exit code 3) after successful infrastructure preparation.
This is intentional; it is not a working whole-ERP startup yet.

Safety: no Git commit/push, Docker volume deletion, password rotation or automatic
cleanup of local IntelliJ user settings.
