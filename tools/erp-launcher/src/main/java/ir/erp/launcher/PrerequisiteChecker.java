package ir.erp.launcher;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static ir.erp.launcher.StartupReport.Level.*;
import static ir.erp.launcher.StartupReport.Result.*;
import static ir.erp.launcher.StartupReport.Stage.PREFLIGHT;

/** Non-mutating preflight. Running it twice never creates files or containers. */
final class PrerequisiteChecker {
  private final Path root;
  private final CommandRunner runner;
  private final StartupReport report;

  PrerequisiteChecker(Path root, StartupReport report) {
    this.root = root;
    this.runner = new CommandRunner(root, java.util.Set.of());
    this.report = report;
  }

  void inspect() {
    long start = System.nanoTime();
    boolean javaOkay = Runtime.version().feature() == VersionPolicy.JAVA_FEATURE;
    record("java", javaOkay ? INFO : ERROR, javaOkay ? PASS : FAIL,
        "Java " + Runtime.version().feature() + " detected, expected Java 21",
        javaOkay ? "" : ToolInstaller.forTool("java").action(), start);

    start = System.nanoTime();
    MavenDetector.MavenStatus mvn = MavenDetector.inspect(root, runner);
    record("maven", mvn.found() && mvn.versionSupported() ? INFO : WARN,
        mvn.found() && mvn.versionSupported() ? PASS : WARNING,
        mvn.description(), mvn.action(), start);
    String cache = MavenDetector.cacheHint(root);
    report.add(PREFLIGHT, "maven-offline", cache.startsWith("Cached") ? INFO : WARN,
        cache.startsWith("Cached") ? PASS : WARNING, cache,
        "For full offline verification run mvn -o -B -ntp verify explicitly after checking mirror availability.", 0);
    String mirror = MavenDetector.mirrorHint();
    if (!mirror.isBlank()) report.add(PREFLIGHT, "maven-mirror", INFO, PASS, mirror,
        "Inspect ~/.m2/settings.xml without sharing credentials; check network access to the configured mirror.", 0);

    start = System.nanoTime();
    DockerToolDetector.DockerStatus d = DockerToolDetector.inspect(runner);
    record("docker-cli", d.cli() ? INFO : ERROR, d.cli() ? PASS : FAIL,
        d.cliDetail(), d.cli() ? "" : ToolInstaller.forTool("docker").action(), start);
    start = System.nanoTime();
    record("docker-compose", d.compose() ? INFO : ERROR, d.compose() ? PASS : FAIL,
        d.composeDetail(), "Install or enable Docker Compose v2 through Docker Desktop.", start);
    start = System.nanoTime();
    record("docker-engine", d.linuxEngine() ? INFO : ERROR, d.linuxEngine() ? PASS : FAIL,
        d.engineDetail(), "Start Docker Desktop, select Linux containers and verify WSL 2; no volumes are removed.", start);

    start = System.nanoTime();
    String os = System.getProperty("os.name", "unknown");
    String arch = System.getProperty("os.arch", "unknown");
    record("platform", INFO, PASS, os + ", " + arch + ", "
        + Runtime.getRuntime().availableProcessors() + " logical CPUs", "", start);

    start = System.nanoTime();
    if (os.toLowerCase(Locale.ROOT).contains("win")) {
      CommandRunner.Result wsl = runner.run(Duration.ofSeconds(7), false, List.of("wsl.exe", "--status"));
      record("wsl2", wsl.succeeded() ? INFO : WARN, wsl.succeeded() ? PASS : WARNING,
          wsl.succeeded() ? "WSL status query succeeded" : "Could not confirm WSL status",
          "Check Windows Features, WSL 2 and virtualization requirements in Docker Desktop setup.", start);
    }

    start = System.nanoTime();
    try {
      FileStore store = Files.getFileStore(root);
      long free = store.getUsableSpace();
      boolean enough = free >= 4L * 1024 * 1024 * 1024;
      record("disk", enough ? INFO : WARN, enough ? PASS : WARNING,
          "Available disk: " + (free / (1024 * 1024)) + " MiB",
          "Reserve at least 4 GiB for Maven artifacts and infrastructure images.", start);
    } catch (Exception ex) {
      record("disk", WARN, WARNING, "Disk capacity unavailable", "Check available drive space.", start);
    }
    start = System.nanoTime();
    long ram = physicalMemoryBytes();
    if (ram > 0) {
      boolean enough = ram >= 8L * 1024 * 1024 * 1024;
      record("memory", enough ? INFO : WARN, enough ? PASS : WARNING,
          "Physical RAM: " + (ram / (1024 * 1024)) + " MiB",
          "8 GiB or more recommended for multiple local JVM processes.", start);
    } else record("memory", WARN, WARNING, "Physical memory unknown", "Check host memory manually.", start);

    start = System.nanoTime();
    Path local = root.resolve(".local");
    Path writable = Files.exists(local) ? local : root;
    boolean ok = Files.isDirectory(writable) && Files.isWritable(writable)
        && !Files.isSymbolicLink(local);
    record("workspace", ok ? INFO : ERROR, ok ? PASS : FAIL,
        ok ? "Workspace is writable" : "Workspace is not safely writable",
        "Grant write access to the repository and ensure .local is not a symlink.", start);

    int gatewayPort = 8080, dbPort = 15432;
    start = System.nanoTime();
    if (Files.exists(root.resolve(".env"))) {
      try {
        LocalEnvironment env = LocalEnvironment.checkWithoutWriting(root);
        gatewayPort = env.gatewayPort();
        dbPort = Integer.parseInt(env.get("ERP_DB_HOST_PORT"));
        record("environment", INFO, PASS, ".env present and valid (secrets not displayed)", "", start);
      } catch (RuntimeException e) {
        record("environment", ERROR, FAIL, ".env invalid: " + e.getClass().getSimpleName(),
            "Review .env keys and duplicate entries; never disclose or rotate the stored secrets automatically.", start);
      }
    } else {
      record("environment", WARN, WARNING, ".env missing (Doctor did not create it)",
          "Use --env-only to create local configuration; no existing credentials are overwritten.", start);
    }
    inspectPort("gateway-port", gatewayPort);
    inspectPort("postgres-port", dbPort);
    for (int i = 0; i < 7; i++) inspectPort("ide-port-" + (18000 + i), 18000 + i);
  }

  private void inspectPort(String name, int port) {
    long start = System.nanoTime();
    boolean bound = PortInspector.listening(port);
    record(name, bound ? WARN : INFO, bound ? WARNING : PASS,
        "localhost:" + port + (bound ? " accepts TCP connections; owner unknown" : " is free"),
        bound ? "Confirm whether the port belongs to an existing ErpYar service; never terminate it blindly." : "", start);
  }

  private static long physicalMemoryBytes() {
    try {
      var bean = ManagementFactory.getOperatingSystemMXBean();
      if (bean instanceof com.sun.management.OperatingSystemMXBean detailed) {
        return detailed.getTotalMemorySize();
      }
    } catch (Exception ignored) { /* Informative only. */ }
    return -1;
  }
  private void record(String id, StartupReport.Level level, StartupReport.Result result,
                      String message, String remedy, long startNs) {
    report.add(PREFLIGHT, id, level, result, message, remedy,
        Duration.ofNanos(System.nanoTime() - startNs).toMillis());
  }
}
