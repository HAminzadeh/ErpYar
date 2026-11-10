package ir.erp.launcher;

import java.util.List;

/** Phase 02 extension: only infrastructure tools may ever run in Docker. */
interface InfrastructureBootstrap {
  record Preparation(boolean implemented, List<String> requiredImages, String explanation) { }
  Preparation describe();
  void startInfrastructure();
}
