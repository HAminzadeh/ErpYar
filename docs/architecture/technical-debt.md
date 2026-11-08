# Phase 00 — technical debt and risk register

Source evidence: `main@1c0ba491ec02f1bd380028b50dd232398a64f197` inspected on 2026-11-08. Priority is an **engineering proposal**, not confirmation of exploitability.

| ID | Severity | Evidence and impact | Next action / acceptance |
|---|---|---|---|
| SEC-01 | Critical before production | `AuditController.append()` trusts any JWT-bearing user with a company grant; `AuditPublisher` is best effort and uses caller's JWT. Application audit events may be forged or lost. | Service identity + reliable outbox/broker + immutable audit policy, denied client writes, verified replay/retry |
| SEC-02 | High | `Access` checks JWT company claims at controller level; no DB RLS, and authorization depends on all future code paths calling checks. | Add comprehensive IDOR tests and defense in depth at repository/RLS layer; prove cross-company denial on every endpoint |
| SEC-03 | High | `IdentityController` issues 15 min HS256 JWT; no revocation/MFA/SSO/rate limiting; permissions not revoked until expiry. | OIDC, rotation, MFA, short-lived access token strategy, revocation/session controls, login abuse tests |
| IAM-01 | High | `AccessUpdate` accepts company/holding UUIDs without inter-service existence verification. | Validate grants against organization API through resilient authenticated client and consistency contract |
| DATA-01 | High | A shared DB PostgreSQL user (`erp`) is reused by all logical databases in Compose, allowing broad DB privileges. | DB-per-service credentials, least privilege, migration roles, secret management |
| WF-01 | Medium | Approval JPA entity has `@Version`, but race/conflict behavior is not exercised by tests. | Transactional concurrency tests proving one terminal decision and HTTP 409 on stale concurrent write |
| API-01 | Medium | JPA entities are directly returned; many list endpoints use unbounded `findAll()` / list calls. | DTOs, pagination, length caps, validation and stable error schema |
| OBS-01 | Medium | Only health/info actuator exposed; no standard tracing/metrics/alerts. | Correlation IDs, tracing, metrics, logs and alert thresholds |
| OPS-01 | Medium | No reproducible CI quality gate at baseline; repository contains legacy source ZIP and large generated source script. | Add Phase 00 structural checks and Java/Compose CI; review/remediate tracked legacy binary independently |
| TEST-01 | High | Only one Java unit test existed in inspected repo; `smoke_test.py` creates records and isn't a full integration suite. | Unit + integration + security/migration suite, disposable test DB and controlled smoke fixtures |
| BIZ-01 | Medium | Multi-step BPM, consolidated holding finance and business modules beyond MVP not present. | Backlog for later functional phases, API contracts and tenant-safe schema designs |

## Rule for phase scope

Phase 00 adds audit evidence, safe verifiers, CI configuration and non-invasive authorization regression tests. Critical security refactoring above is **not falsely marked fixed**: such redesign requires isolated application/integration tests and dedicated deployment migration plans. There is no newly verified build-blocking defect that would justify rewriting current service code or V1 migrations in this baseline phase.
