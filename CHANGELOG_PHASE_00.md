# CHANGELOG — ErpYar Phase 00 (2026-11-08)

**Audited base:** `main@1c0ba491ec02f1bd380028b50dd232398a64f197`  
**Delivery:** documentation, tests and non-destructive verification only. **Status: PARTIAL pending workstation CI/runtime checks.**

## Added

- Versioned current-state inventory covering the Java 21 / Spring Boot 4.0.8 / Spring Cloud 2025.1.3 reactor, gateway, six bounded contexts and six Flyway V1 migrations.
- Evidence-based technical-debt and security gap list, including JWT-only context, unsecured audit ingress and best-effort HTTP publisher, and database/tenant-isolation limitations.
- ADR 0001 and Phase 00 acceptance/backlog; operations/rollback/test guide.
- `scripts/verify_baseline.py`, read-only Python standard-library structure/manifest inventory, optional JSON report, no credentials read.
- `scripts/tests/test_verify_baseline.py`, three local isolated regression tests.
- `scripts/phase00_gateway_check.py`, read-only gateway health and anonymous-access checks (run only when services are up).
- `CompanyScopeBoundaryTest.java`, four JUnit assertions for STAFF / COMPANY_ADMIN / HOLDING_ADMIN company/holding scope boundaries.
- GitHub Actions quality gate on PR, main push and manual dispatch: Java 21 Maven verify, Python checks and Docker Compose config validation with non-production placeholder variables.

## Preserved / unchanged

- All runtime Java controllers/services, APIs, permissions, existing tests, JPA entities and Flyway V1 migrations.
- Dockerfile, Compose, existing `.env` generation and existing smoke test; no data, credential, automatic Git commit or push changes.
- Phase 00 adds **zero** schema changes, so no database rollback is necessary.

## Actual validation performed while creating this package

- `python -m py_compile` of new Python scripts: **PASS**.
- `python -m unittest discover` on the three new synthetic-fixture baseline tests: **PASS (3)**.
- ZIP payload file-hash matching, manifest/path validation, and unsafe archive-entry checks: **PASS** when packaging (see delivery report).
- Full checkout `mvn verify`: **NOT RUN**, Maven unavailable here.
- `docker compose config`, Gateway health, clean bootstrap, `scripts/smoke_test.py` and live cross-tenant assertions: **NOT RUN**, Docker and running stack unavailable here.
- New Java JUnit tests: **NOT RUN**, Maven dependencies/real checkout not available here.
- PowerShell installer execution: **NOT RUN** in current Linux environment, PowerShell unavailable. Installer source reviewed; use on Windows PowerShell.

**Known blocker:** runtime acceptance is pending. Existing tracked binary `erp-phase1-source.zip` was excluded from this patch and is not attested as free of hidden secrets; review it separately.
