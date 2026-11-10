package ir.erp.launcher;

import java.util.List;

/** Explicit phase boundary; prevents accidentally starting Java ERP containers. */
final class InfrastructureManager implements InfrastructureBootstrap {
  @Override public Preparation describe() {
    return new Preparation(false, List.of("postgres:16-alpine"),
        "Phase 02 will manage PostgreSQL as infrastructure-only Docker Compose, image cache and authentication. "
        + "No ERP Java service is allowed inside Docker.");
  }
  @Override public void startInfrastructure() {
    throw new StartupException("Infrastructure bootstrap is reserved for Phase 02. "
        + "Phase 01 will not run the legacy all-services Docker Compose.");
  }
}
