package ir.erp.launcher;

import java.nio.file.Path;

/**
 * Legacy facade retained for binary/source compatibility during migration.
 * NEVER launches the old Compose stack containing Java ERP services.
 * Phase 02 will supply a dedicated infrastructure-only implementation.
 */
@Deprecated(forRemoval = true)
public final class ComposeOrchestrator {
  public ComposeOrchestrator(Path root, LocalEnvironment environment, ErpYarLauncher.Options options) {
    if (!ProjectRoot.valid(root)) throw new StartupException("Invalid ErpYar checkout");
  }
  public static void checkTools(Path root, boolean autoStartDockerDesktop) {
    DockerToolDetector.DockerStatus tools = DockerToolDetector.inspect(
        new CommandRunner(root, java.util.Set.of()));
    if (!tools.cli() || !tools.compose() || !tools.linuxEngine()) {
      throw new StartupException("Docker CLI/Compose v2/Linux Engine prerequisite not ready. "
          + "Run ErpYar --doctor for non-mutating diagnostics.");
    }
  }
  public void start() {
    throw new StartupException("Legacy Docker orchestration of Java services was disabled in Phase 01. "
        + "Only infrastructure may run in Docker; see Phase 02 and Phase 04.");
  }
  public void runSmokeTest() {
    throw new StartupException("Legacy Docker-mode smoke test is disabled. "
        + "Phase 05 will add opt-in data-safe verification for selected host Java services.");
  }
}
