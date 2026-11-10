package ir.erp.launcher;

import java.util.List;

/** Phase 03 extension: deployment/tenant module decisions live here, not in Docker. */
interface ModulePlanProvider {
  record ModulePlan(boolean resolved, List<String> selectedServices, String reason) { }
  ModulePlan plan();
}
