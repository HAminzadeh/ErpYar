package ir.erp.launcher;

import java.util.List;

/** Explicitly refuses to fake successful start before Phase 04. */
final class JavaServiceRuntime implements JavaProcessSupervisor {
  @Override public String describe() {
    return "Host JVM service management is deferred until Phase 04; "
        + "no gateway/identity/organization/master-data/workflow/notification/audit processes were started.";
  }
  @Override public void startSelected(List<String> services) {
    throw new StartupException("Selected Java process orchestration is not implemented before Phase 04. "
        + "Do not infer service readiness from infrastructure health.");
  }
}
