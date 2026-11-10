package ir.erp.launcher;

import java.nio.file.Path;
import java.time.Duration;

import static ir.erp.launcher.StartupReport.Level.*;
import static ir.erp.launcher.StartupReport.Result.*;
import static ir.erp.launcher.StartupReport.Stage.*;

/** One deliberate entry point; never claims readiness of unimplemented phases. */
final class StartupOrchestrator {
  record Execution(StartupReport report, int exitCode) {
    Execution { report.exitCode(exitCode); }
  }

  private final Path root;
  private final ErpYarLauncher.Options options;
  private final InfrastructureBootstrap infrastructure = new InfrastructureManager();
  private final ModulePlanProvider modulePlans = new Phase1ModulePlanProvider();
  private final JavaProcessSupervisor javaServices = new JavaServiceRuntime();

  StartupOrchestrator(Path root, ErpYarLauncher.Options options) {
    this.root = root;
    this.options = options;
  }

  Execution execute() {
    StartupReport report = new StartupReport(options.mode());
    if (options.envOnly) {
      long start = System.nanoTime();
      try {
        LocalEnvironment.ensure(root);
        report.add(CONFIG, "environment", INFO, PASS, "Local .env validated/created with existing secrets preserved", "", ms(start));
        report.outcome("ENVIRONMENT_READY");
        return new Execution(report, 0);
      } catch (StartupException ex) {
        report.add(CONFIG, "environment", ERROR, FAIL, "Cannot prepare .env", ex.getMessage(), ms(start));
        report.outcome("FAILED");
        return new Execution(report, 2);
      }
    }

    // --start may wake an ALREADY INSTALLED Docker Desktop; Doctor/Plan cannot do so.
    if (options.start && options.startDockerDesktop) {
      DockerToolDetector.tryStartExistingDesktop(new CommandRunner(root, java.util.Set.of()));
    }
    new PrerequisiteChecker(root, report).inspect();
    if (options.doctor) {
      report.outcome(report.hasErrors() ? "DIAGNOSTIC_WITH_ERRORS" : "DIAGNOSTIC_COMPLETE");
      return new Execution(report, report.hasErrors() ? 2 : 0);
    }
    if (options.plan) {
      describeDeferredPhases(report);
      report.outcome(report.hasErrors() ? "PLAN_WITH_PREREQUISITE_ERRORS" : "PLAN_ONLY_NOT_STARTED");
      return new Execution(report, report.hasErrors() ? 2 : 0);
    }
    if (report.hasErrors()) {
      report.add(READY, "startup", ERROR, FAIL, "Required prerequisites are not ready",
          "Fix ERROR checks and run --doctor before trying again.", 0);
      report.outcome("FAILED");
      return new Execution(report, 2);
    }
    long start = System.nanoTime();
    try {
      LocalEnvironment.ensure(root);
      report.add(CONFIG, "environment", INFO, PASS, "Local configuration present; no credentials logged", "", ms(start));
    } catch (StartupException ex) {
      report.add(CONFIG, "environment", ERROR, FAIL, "Cannot validate local environment", ex.getMessage(), ms(start));
      report.outcome("FAILED");
      return new Execution(report, 2);
    }
    describeDeferredPhases(report);
    report.add(READY, "startup", WARN, DEFERRED,
        "Not ready: selected Java services are NOT running; phase 02/03/04 integration pending",
        "Do not treat Phase 01 as complete customer application startup.", 0);
    report.outcome("DEFERRED_NOT_READY");
    return new Execution(report, 3);
  }

  private void describeDeferredPhases(StartupReport report) {
    InfrastructureBootstrap.Preparation infrastructurePlan = infrastructure.describe();
    report.add(INFRA_PREP, "infrastructure", WARN, DEFERRED, infrastructurePlan.explanation(),
        "Phase 02: pinned offline image cache and PostgreSQL-only Compose.", 0);
    ModulePlanProvider.ModulePlan modulePlan = modulePlans.plan();
    report.add(SERVICE_PLAN, "module-plan", WARN, DEFERRED, modulePlan.reason(),
        "Phase 03: module registry, tenant entitlements, dependency graph.", 0);
    report.add(SERVICE_PLAN, "java-runtime", WARN, DEFERRED, javaServices.describe(),
        "Phase 04: start only selected independent JVM processes.", 0);
  }

  private static long ms(long started) { return Duration.ofNanos(System.nanoTime() - started).toMillis(); }
}
