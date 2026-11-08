# ErpYar — Phase 00 execution record

**Phase:** 00 — ممیزی و تثبیت مبنا  
**Base commit:** `1c0ba491ec02f1bd380028b50dd232398a64f197` (`main`)  
**Review date:** 2026-11-08  
**Status:** **PARTIAL / runtime validation pending**

## Coverage against phase acceptance

| Requirement | Current evidence | Status |
|---|---|---|
| Branches / commit | GitHub branches endpoint lists `main`, SHA recorded above; recent commit metadata inspected | DONE (observed at review time) |
| File/module map | 8 Maven child modules and 7 deployable services; gateway routes, DBs, V1 migrations inventoried | DONE, source inspection |
| README / Compose / migration / controllers / tests | Inspected source files recorded in `current-state.md` | DONE, source inspection |
| Historical phase claims | Phase-one commit claims healthy containers and smoke checks; those claims cannot replace local runtime evidence | DOCUMENTED; not independently verified |
| Debt/gap report and ADR | `technical-debt.md`, `adr/0001-phase-00-baseline-as-evidence.md` | DONE |
| Blocking fixes | No build-blocking defect established by an actual local build; runtime code intentionally not rewritten | NO VERIFIED FIX REQUIRED; re-evaluate after build |
| Secret hygiene | `.env` ignored; `.dockerignore` excludes `.env`; archived legacy ZIP not repackaged; package payload contains no credentials | PARTIAL: binary archive needs separate inspection |
| Reproducible verification | Python structural audit, offline unit checks, Java authorization tests, CI Maven+Compose checks | IMPLEMENTED, downstream tools still pending |
| `mvn -B -ntp verify` | Maven absent from artifact environment, full checkout not mounted | NOT_RUN |
| `docker compose config --quiet` | Docker absent from artifact environment | NOT_RUN |
| Local gateway health / clean bootstrap | Running containers/DB unavailable | NOT_RUN |
| `scripts/smoke_test.py` including cross-company denial | Requires running stack, `.env`, creates local demo rows | NOT_RUN |
| Migration test | V1 SQL inspected and preserved; no new migrations | INSPECTED / live migration NOT_RUN |

## Delivered files

- `scripts/phase00_gateway_check.py`: read-only local gateway health and anonymous-access checks after services start.
- `scripts/verify_baseline.py`: reproducible read-only local inventory with machine-readable JSON output option.
- `scripts/tests/test_verify_baseline.py`: three hermetic stdlib regression tests of inventory detection.
- `libs/erp-common/src/test/java/ir/erp/common/CompanyScopeBoundaryTest.java`: four security-boundary JUnit checks (Java execution pending).
- `.github/workflows/phase-00-baseline.yml`: PR/main/manual quality gate: verify structure; Python tests; Maven verify; Compose config.
- `docs/architecture/current-state.md`, `docs/architecture/technical-debt.md`, `docs/architecture/adr/0001-phase-00-baseline-as-evidence.md`, `docs/roadmap/phase-00.md`, `docs/operations/baseline-validation.md`, `CHANGELOG_PHASE_00.md`.

No APIs, events, schema migration, secrets or application logic changes are included. No database reset or bootstrap mutation is performed by Phase 00 itself.

## Follow-on backlog ordering

1. **P0**: Run Maven/Compose/smoke on the real checked-out baseline; record failures before making compatibility changes.
2. **P0**: Secure audit ingestion and delivery (SEC-01); ban arbitrary clients from writing audit records.
3. **P0**: Complete cross-company/tenant and authorization denial coverage; add server-enforced tenant isolation (SEC-02).
4. **P1**: Token/session lifecycle, MFA/SSO, rate limiting, granularity of grants (SEC-03 / IAM-01).
5. **P1**: JPA/DTO separation, pagination and optimistic concurrency integration tests.
6. **P1**: Robust CI container integration, tracing, migration rollback strategies and per-service DB credentials.

## Release gate

Do not call Phase 00 **complete** before the exact base has passed local Maven verify, Compose config, gateway health, isolated first boot and cross-company smoke under controlled test data. The installer and scripts do not claim to have performed these operations remotely.
