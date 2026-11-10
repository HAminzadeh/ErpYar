package ir.erp.launcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** IntelliJ entry point: Phase 01 doctor/config/plan; subsequent phases supply actual startup. */
public final class ErpYarLauncher {
  private ErpYarLauncher() { }

  public static void main(String[] args) {
    try {
      Options options = Options.parse(args);
      if (options.help) { printHelp(); return; }
      Path root = ProjectRoot.find(options.repoPath);
      StartupOrchestrator.Execution result = new StartupOrchestrator(root, options).execute();
      System.out.println(options.json ? result.report().toJson() : result.report().toConsole());
      if (result.exitCode() != 0) System.exit(result.exitCode());
    } catch (StartupException ex) {
      System.err.println("[ErpYar] STARTUP FAILED: " + ex.getMessage());
      System.exit(2);
    } catch (Exception ex) {
      // Never print raw ProcessBuilder environment or secret values.
      System.err.println("[ErpYar] Unexpected failure: " + ex.getClass().getSimpleName());
      System.exit(2);
    }
  }

  static void printHelp() {
    System.out.println("ErpYar Phase 01 bootstrap / diagnostics (Java 21)");
    System.out.println("  --repo <path>        Checkout root or an existing nested directory");
    System.out.println("  --doctor | --check  Read-only diagnostics, no file or Docker changes");
    System.out.println("  --env-only          Create/validate missing .env keys; preserve existing secrets");
    System.out.println("  --plan              Read-only phases 02-04 implementation plan (NOT service startup)");
    System.out.println("  --start             Prepare env; full service startup remains DEFERRED until phase 04");
    System.out.println("  --no-docker-desktop Do not start installed Docker Desktop in --start mode");
    System.out.println("  --json              Emit machine-readable diagnostics (no credentials)");
    System.out.println("  --help              Show options");
    System.out.println("Exit: 0 diagnostics/plan/config complete, 2 prerequisite/config failure, 3 startup deferred.");
  }

  static final class Options {
    Path repoPath;
    boolean doctor;
    boolean envOnly;
    boolean plan;
    boolean start;
    boolean startDockerDesktop = true;
    boolean json;
    boolean help;
    boolean skipBuildDeprecated;

    String mode() { return doctor ? "DOCTOR" : envOnly ? "ENV_ONLY" : plan ? "PLAN" : "START"; }
    static Options parse(String[] args) {
      Options result = new Options();
      List<String> tokens = Arrays.asList(args);
      for (int i = 0; i < tokens.size(); i++) {
        String value = tokens.get(i);
        switch (value) {
          case "--help" -> result.help = true;
          case "--doctor", "--check" -> result.doctor = true;
          case "--env-only" -> result.envOnly = true;
          case "--plan" -> result.plan = true;
          case "--start" -> result.start = true;
          case "--json" -> result.json = true;
          case "--no-docker-desktop" -> result.startDockerDesktop = false;
          case "--skip-build" -> result.skipBuildDeprecated = true; // old CLI, no Java Docker builds.
          case "--smoke" -> throw new StartupException("--smoke is unavailable until Phase 05 and is never implicit.");
          case "--repo" -> {
            if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--"))
              throw new StartupException("--repo expects an existing directory path");
            result.repoPath = Path.of(tokens.get(++i));
          }
          default -> throw new StartupException("Unknown option " + value + "; run --help");
        }
      }
      int count = (result.doctor ? 1 : 0) + (result.envOnly ? 1 : 0)
          + (result.plan ? 1 : 0) + (result.start ? 1 : 0);
      if (count > 1) throw new StartupException("Choose only one of --doctor, --plan, --env-only or --start");
      if (count == 0 && !result.help) result.start = true;
      return result;
    }
  }
}
