package ir.erp.launcher;

import java.util.List;

/** Phase 04 extension for independent host JVM processes with stop/restart semantics. */
interface JavaProcessSupervisor {
  String describe();
  void startSelected(List<String> services);
}
