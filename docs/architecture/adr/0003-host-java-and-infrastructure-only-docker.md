# ADR 0003 — Host JVM processes; Docker reserved for infrastructure

Status: **Accepted (Phase 01 contract)**

## Context

The pre-phase-01 `ComposeOrchestrator` ran all seven Spring Boot services as Docker images. This conflicts with the target developer workflow: modules enabled per customer/deployment must run as independent host JVM processes, with IntelliJ debug and no application image rebuild. The older `compose.yaml` contains application service definitions and may still be used manually by legacy environments; it is retained temporarily to avoid unreviewed breaking changes.

## Decision

1. Java 21 `erp-launcher` owns preflight, configuration, module planning and later JVM process supervision. All ERP Spring Boot services run outside Docker in the target development architecture.
2. Docker only hosts infrastructure tools. PostgreSQL 16 is the currently known dependency; Redis/MinIO/RabbitMQ are not introduced without confirmed service requirements.
3. `ComposeOrchestrator.start()` in Phase 01 fails safely rather than starting the obsolete all-application Compose stack. The legacy IntelliJ Run Configuration has been converted to a read-only plan preview.
4. `StartupOrchestrator` reports explicit `DEFERRED` stages until Phase 02 (infrastructure-only Docker/image cache), Phase 03 (deployment/tenant module registry) and Phase 04 (host JVM supervisor and access enforcement) are complete. No `READY` signal is fabricated.
5. Dedicated deployments launch the dependency closure for one customer; shared Multi-Tenant deployments launch the union of services required by all enabled tenants. Access at tenant, holding and company scopes is independent of the runtime process set and enforced in backends, not only in Gateway/menu.
6. Docker Image TAR cache is planned under ignored `.local/docker-images/` with digest, OS/arch and SHA-256; only infrastructure images are eligible. Phase 01 does not generate fake TAR files.

## Ownership boundaries

- `PrerequisiteChecker` — read-only host diagnostics.
- `InfrastructureBootstrap` / `InfrastructureManager` — Phase 02 boundary; only infrastructure.
- `ModulePlanProvider` — Phase 03 contract; entitlements/dependencies.
- `JavaProcessSupervisor` / `JavaServiceRuntime` — Phase 04 boundary; host JVM processes only.
- `ToolInstaller` — no privileged changes without explicit approval; Phase 01 provides vetted manual installation guidance.

## Consequences and migration

- Users who formerly ran `ErpYar - Start All Services` now see `legacy preview` and must use the new phase-aware configurations. Existing `application-ide.yml` files remain supported. In Phase 02, define a new PostgreSQL-only Compose file; in Phase 04, introduce the actual selected-Java-service launch plan.
- The old root `compose.yaml` remains in Git in Phase 01. Manual `docker compose up` against that file is **legacy behavior** and not the supported launch path.
- Production deployments may use separate process supervisors/Kubernetes in the future, but development launcher behavior must not silently mutate production databases or credentials.

## Verification

Java 21 compile and read-only CLI contract tests; JUnit ProjectRoot regression and mock-free offline tests. Maven dependency resolution and Windows/Docker behavior require execution on a prepared machine and must be reported separately.
