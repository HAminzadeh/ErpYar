package ir.erp.launcher;

import java.util.List;

/** Returns an explicitly unresolved plan until the Phase 03 registry exists. */
final class Phase1ModulePlanProvider implements ModulePlanProvider {
  @Override public ModulePlan plan() {
    return new ModulePlan(false, List.of(), "No customer/tenant entitlements are loaded. "
        + "Phase 03 must resolve enabled modules and their service dependency union.");
  }
}
