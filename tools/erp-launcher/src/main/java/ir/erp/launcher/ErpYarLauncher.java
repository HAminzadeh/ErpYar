package ir.erp.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** IntelliJ entry point: configure the local environment and launch the entire ERP through Compose. */
public final class ErpYarLauncher {
  private ErpYarLauncher() { }

  public static void main(String[] args) {
    try {
      Options options = Options.parse(args);
      if (options.help) {
        printHelp();
        return;
      }
      Path root = ProjectRoot.find(options.repoPath);
      System.out.println("[ErpYar] Project: " + root);
      System.out.println("[ErpYar] Java runtime: " + Runtime.version());
      if (Runtime.version().feature() < 21) {
        throw new StartupException("Java 21 or newer is required. Set Project SDK to JDK 21 in IntelliJ.");
      }
      if (options.checkOnly) {
        LocalEnvironment.checkWithoutWriting(root);
        ComposeOrchestrator.checkTools(root, false);
        System.out.println("[ErpYar] Preflight checks completed; nothing was changed.");
        return;
      }
      LocalEnvironment environment = LocalEnvironment.ensure(root);
      System.out.println("[ErpYar] Local secrets present; values are intentionally not printed.");
      if (options.envOnly) {
        System.out.println("[ErpYar] Local configuration is ready; no containers started.");
        return;
      }
      ComposeOrchestrator orchestrator = new ComposeOrchestrator(root, environment, options);
      orchestrator.start();
      if (options.smoke) {
        orchestrator.runSmokeTest();
      }
      System.out.println("[ErpYar] Startup completed successfully. Gateway: http://127.0.0.1:"
          + environment.gatewayPort());
    } catch (StartupException e) {
      System.err.println("[ErpYar] STARTUP FAILED: " + e.getMessage());
      System.exit(1);
    } catch (Exception e) {
      System.err.println("[ErpYar] Unexpected launcher failure (no secrets printed): "
          + e.getClass().getSimpleName() + ": " + e.getMessage());
      System.exit(1);
    }
  }

  static void printHelp() {
    System.out.println("ErpYar IntelliJ/Docker development launcher (JDK 21)");
    System.out.println("Options:");
    System.out.println("  --repo <path>              Override repository root (otherwise locate parent pom.xml)");
    System.out.println("  --check                    Read-only checks; do not create .env or start Docker");
    System.out.println("  --env-only                 Create/check .env then stop");
    System.out.println("  --skip-build               Reuse existing images on startup");
    System.out.println("  --no-docker-desktop        Do not attempt to start Docker Desktop on Windows");
    System.out.println("  --smoke                    Run DB-writing smoke test after successful startup");
    System.out.println("  --help                     Show this help");
  }

  static final class Options {
    Path repoPath;
    boolean checkOnly;
    boolean envOnly;
    boolean skipBuild;
    boolean startDockerDesktop = true;
    boolean smoke;
    boolean help;

    static Options parse(String[] args) {
      Options result = new Options();
      List<String> options = new ArrayList<>(Arrays.asList(args));
      for (int i = 0; i < options.size(); i++) {
        String option = options.get(i);
        switch (option) {
          case "--help" -> result.help = true;
          case "--check" -> result.checkOnly = true;
          case "--env-only" -> result.envOnly = true;
          case "--skip-build" -> result.skipBuild = true;
          case "--no-docker-desktop" -> result.startDockerDesktop = false;
          case "--smoke" -> result.smoke = true;
          case "--repo" -> {
            if (i + 1 >= options.size()) {
              throw new StartupException("--repo expects an absolute or relative folder path");
            }
            result.repoPath = Path.of(options.get(++i));
          }
          default -> throw new StartupException("Unknown option: " + option + ". Use --help.");
        }
      }
      if (result.checkOnly && (result.envOnly || result.smoke)) {
        throw new StartupException("--check cannot be combined with --env-only or --smoke");
      }
      if (result.envOnly && result.smoke) {
        throw new StartupException("--env-only and --smoke are mutually exclusive");
      }
      return result;
    }
  }
}
