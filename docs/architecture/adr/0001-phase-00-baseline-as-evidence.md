# ADR 0001 — Phase 00 baseline as versioned evidence

**Date:** 2026-11-08  
**Status:** Accepted for Phase 00  
**Base:** `HAminzadeh/ErpYar`, `main`, `1c0ba491ec02f1bd380028b50dd232398a64f197`

## Context

ErpYar already contains a Java 21 Maven reactor, six service-owned PostgreSQL databases and basic authorization code. The Phase 00 domain nouns (architecture inventory, baseline report, technical debt and Git reference) are *engineering artifacts*, not tenant-owned transactional business records. Creating another database and API solely to persist them increases operational scope and can violate service ownership.

## Decision

1. Keep a source-anchored architecture inventory, technical-debt register, operational guide and numbered roadmap in `docs/`.
2. Use `scripts/verify_baseline.py` (read-only, Python standard library) for reproducible structural checks, and a GitHub Actions quality gate for Java 21 Maven `verify` and `docker compose config`.
3. Add unit security-boundary assertions to `erp-common` without changing authentication behavior or exposing new endpoints.
4. Do not modify any Flyway V1 migration, domain schema, or runtime API in Phase 00. Use `main`'s exact full SHA as the package prerequisite and fail closed when the checkout diverges.
5. Package only changed/new files, with a manifest SHA-256 list, and install using a local PowerShell script with clean-Git/SHA checks, backups, validation and rollback on recoverable errors.
6. Separate `PASS`, `FAIL`, `NOT_RUN` and `BLOCKED` truthfully. Source inventory and offline tests never count as E2E evidence.

## Consequences

**Positive:** non-destructive, reviewable, repeatable; enables future phases to compare against a known code baseline.  
**Tradeoffs:** the local Maven build, Compose startup and security smoke must still run on a workstation or CI; Phases 01+ must address deeper architecture and security gaps.  
**Rollback:** revert new docs/tests/workflow/script files from Git after explicit review or use installer backup; database rollback is unnecessary because this phase has **zero migrations**.
