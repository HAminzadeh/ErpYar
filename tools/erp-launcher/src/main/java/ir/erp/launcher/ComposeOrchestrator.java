package ir.erp.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns local Docker Compose service lifecycle. Never destroys volumes or resets credentials. */
public final class ComposeOrchestrator {
  private static final Duration QUICK = Duration.ofSeconds(20);
  private static final Duration COMPOSE_BUILD = Duration.ofMinutes(20);
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(4);
  private static final List<String> DATABASES = List.of(
      "identity_db", "organization_db", "master_data_db", "workflow_db", "notification_db", "audit_db");
  private static final List<String> SERVICES = List.of(
      "postgres", "gateway", "identity", "organization", "master-data", "workflow", "notification", "audit");
  private final Path root;
  private final LocalEnvironment environment;
  private final ErpYarLauncher.Options options;
  private final CommandRunner processes;

  public ComposeOrchestrator(Path root, LocalEnvironment env, ErpYarLauncher.Options options) {
    this.root = root;
    this.environment = env;
    this.options = options;
    this.processes = new CommandRunner(root, env.secrets());
  }

  static void checkTools(Path root, boolean autoStartDockerDesktop) {
    CommandRunner commands = new CommandRunner(root, java.util.Set.of());
    require(commands.run(QUICK, false, List.of("docker", "--version")),
        "Docker CLI is not installed. Install Docker Desktop, then reopen IntelliJ.");
    require(commands.run(QUICK, false, List.of("docker", "compose", "version")),
        "Docker Compose v2 is not installed. Install/enable Docker Desktop with Compose.");
    if (!commands.run(QUICK, false, List.of("docker", "info", "--format", "{{.ServerVersion}}")).succeeded()) {
      if (autoStartDockerDesktop) {
        startDockerDesktopIfAvailable();
        Instant until = Instant.now().plusSeconds(120);
        while (Instant.now().isBefore(until)) {
          if (commands.run(QUICK, false,
              List.of("docker", "info", "--format", "{{.ServerVersion}}")).succeeded()) {
            System.out.println("[ErpYar] Docker engine ready.");
            return;
          }
          sleep(3);
        }
      }
      throw new StartupException("Docker Engine is not responding. Start Docker Desktop and ensure the Linux "
          + "engine is running; then retry. No database data was changed.");
    }
    System.out.println("[ErpYar] Docker Engine and Compose are available.");
  }

  private static void startDockerDesktopIfAvailable() {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
    Path executable = Path.of("C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe");
    if (!Files.isRegularFile(executable)) return;
    try {
      new ProcessBuilder(executable.toString()).start();
      System.out.println("[ErpYar] Requested Docker Desktop startup; waiting for the engine.");
    } catch (IOException e) {
      System.out.println("[ErpYar] Docker Desktop could not be started automatically. Open it manually.");
    }
  }

  public void start() {
    checkTools(root, options.startDockerDesktop);
    optionalTool("git", "--version");
    optionalTool("mvn", "--version");
    command(QUICK, false, "Invalid Docker Compose settings. Review compose.yaml and .env.",
        compose("config", "--quiet"));
    System.out.println("[ErpYar] Docker Compose configuration: OK");

    command(Duration.ofMinutes(3), true, "Cannot start PostgreSQL container. Check docker compose logs postgres.",
        compose("up", "-d", "postgres"));
    waitForPostgres();
    verifyDatabasePassword();
    ensureDatabases();

    List<String> start = compose("up", "-d");
    if (!options.skipBuild) start.add("--build");
    command(COMPOSE_BUILD, true, "Docker Compose startup/build failed. Run docker compose ps and logs.", start);
    waitForServices();
    waitForHttpEndpoints();
  }

  public void runSmokeTest() {
    CommandRunner.Result python = processes.run(QUICK, false,
        System.getProperty("os.name", "").toLowerCase().contains("win")
            ? List.of("py", "--version") : List.of("python3", "--version"));
    if (!python.succeeded()) {
      throw new StartupException("--smoke requires Python. Install Python 3.9+ and rerun explicitly with --smoke.");
    }
    System.out.println("[ErpYar] Running smoke test; it creates demo records in local databases.");
    command(Duration.ofMinutes(2), true, "Smoke test failed. Inspect gateway and identity logs.",
        System.getProperty("os.name", "").toLowerCase().contains("win")
            ? List.of("py", "scripts/smoke_test.py") : List.of("python3", "scripts/smoke_test.py"));
    System.out.println("[ErpYar] End-to-end smoke test passed.");
  }

  private void optionalTool(String tool, String arg) {
    if (!processes.run(QUICK, false, List.of(tool, arg)).succeeded()) {
      System.out.println("[ErpYar] Optional local tool unavailable: " + tool
          + " (Docker image builds do not require host Maven).");
    }
  }

  private void waitForPostgres() {
    Instant deadline = Instant.now().plusSeconds(90);
    while (Instant.now().isBefore(deadline)) {
      CommandRunner.Result ready = processes.run(QUICK, false,
          compose("exec", "-T", "postgres", "pg_isready", "-U", "erp", "-d", "postgres"));
      if (ready.succeeded()) {
        System.out.println("[ErpYar] PostgreSQL is accepting connections.");
        return;
      }
      sleep(3);
    }
    throw new StartupException("PostgreSQL did not become ready within 90 seconds. "
        + "Run: docker compose logs postgres (do not remove volumes).");
  }

  private void verifyDatabasePassword() {
    // Comparing hashes avoids printing the credential in a command line or logs.
    String shell = "printf '%s' \"$POSTGRES_PASSWORD\" | sha256sum";
    CommandRunner.Result inside = processes.run(QUICK, false,
        compose("exec", "-T", "postgres", "sh", "-c", shell));
    if (!inside.succeeded()) {
      throw new StartupException("Cannot verify PostgreSQL container environment. "
          + "Ensure the official postgres:16-alpine image is running.");
    }
    Matcher hash = Pattern.compile("(?m)^([0-9a-fA-F]{64})\\s").matcher(inside.tail());
    if (!hash.find() || !sha256(environment.get("DB_PASSWORD")).equalsIgnoreCase(hash.group(1))) {
      throw new StartupException("PostgreSQL container password environment differs from project .env. "
          + "Do not overwrite credentials; inspect Compose environment and existing volume settings.");
    }
    // Local container shell exports its POSTGRES_PASSWORD; no secret appears in argv or output.
    String connection = "PGPASSWORD=\"$POSTGRES_PASSWORD\" "
        + "psql -h 127.0.0.1 -U erp -d postgres -tAc 'SELECT 1'";
    CommandRunner.Result auth = processes.run(QUICK, false,
        compose("exec", "-T", "postgres", "sh", "-c", connection));
    if (!auth.succeeded() || !auth.tail().strip().equals("1")) {
      throw new StartupException("PostgreSQL password authentication failed for role 'erp' (SQLSTATE 28P01). "
          + "The persistent database may have been initialized with an older password. "
          + "No password was rotated and no volume was deleted. "
          + "Use the documented controlled credential reconciliation procedure.");
    }
    System.out.println("[ErpYar] PostgreSQL role password matches .env (no password displayed).");
  }

  private void ensureDatabases() {
    for (String database : DATABASES) {
      String query = "SELECT 1 FROM pg_database WHERE datname='" + database + "'";
      CommandRunner.Result exists = processes.run(QUICK, false,
          compose("exec", "-T", "postgres", "psql", "-U", "erp", "-d", "postgres", "-tAc", query));
      if (!exists.succeeded()) {
        throw new StartupException("Cannot inspect PostgreSQL databases. "
            + "Check permissions for the erp role and PostgreSQL container logs.");
      }
      if (exists.tail().strip().equals("1")) continue;
      System.out.println("[ErpYar] Creating missing logical database: " + database);
      command(QUICK, false, "Cannot create missing " + database + ". Existing databases were not modified.",
          compose("exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", "erp",
              "-d", "postgres", "-c", "CREATE DATABASE " + database));
    }
    System.out.println("[ErpYar] Six service databases are present (Flyway runs on service startup).");
  }

  private void waitForServices() {
    Instant until = Instant.now().plusSeconds(STARTUP_TIMEOUT.toSeconds());
    while (Instant.now().isBefore(until)) {
      CommandRunner.Result result = processes.run(QUICK, false,
          compose("ps", "--services", "--status", "running"));
      if (result.succeeded()) {
        List<String> active = result.tail().lines().map(String::strip).toList();
        if (active.containsAll(SERVICES)) {
          System.out.println("[ErpYar] Eight Compose containers are running.");
          return;
        }
      }
      sleep(3);
    }
    throw new StartupException("Not all eight Compose containers are running. "
        + "Use docker compose ps and docker compose logs --tail=100 to identify the failed service.");
  }

  private void waitForHttpEndpoints() {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    String url = "http://127.0.0.1:" + environment.gatewayPort();
    Map<String, String> endpoints = new LinkedHashMap<>();
    endpoints.put("gateway", "/actuator/health");
    endpoints.put("identity", "/api/auth/me");
    endpoints.put("organization", "/api/holdings");
    endpoints.put("master-data", "/api/master-data/categories");
    endpoints.put("workflow", "/api/workflows/requests");
    endpoints.put("notification", "/api/notifications/mine");
    endpoints.put("audit", "/api/audit/events");
    Instant until = Instant.now().plusSeconds(STARTUP_TIMEOUT.toSeconds());
    String last = "HTTP gateway not accepting connections";
    int attempts = 0;
    while (Instant.now().isBefore(until)) {
      try {
        boolean success = true;
        for (Map.Entry<String, String> endpoint : endpoints.entrySet()) {
          HttpRequest request = HttpRequest.newBuilder(URI.create(url + endpoint.getValue()))
              .timeout(Duration.ofSeconds(3)).GET().build();
          HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
          int status = response.statusCode();
          boolean okay = endpoint.getKey().equals("gateway")
              ? status == 200 && response.body().contains("UP")
              : status == 401;
          if (!okay) {
            last = endpoint.getKey() + " returned HTTP " + status
                + " (expected " + (endpoint.getKey().equals("gateway") ? "200 UP" : "401 anonymous") + ")";
            success = false;
            break;
          }
        }
        if (success) {
          System.out.println("[ErpYar] Gateway is UP; six protected backend routes returned 401 as expected.");
          return;
        }
      } catch (IOException e) {
        last = "gateway/backend not ready: " + e.getClass().getSimpleName();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new StartupException("Interrupted during HTTP readiness checks", e);
      }
      if (++attempts % 5 == 0) System.out.println("[ErpYar] Waiting for service readiness: " + last);
      sleep(3);
    }
    throw new StartupException("Startup timed out: " + last + ". "
        + "Check docker compose logs --tail=100 gateway identity organization master-data workflow notification audit.");
  }

  private static String sha256(String value) {
    try {
      byte[] raw = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(raw);
    } catch (NoSuchAlgorithmException e) {
      throw new StartupException("JDK SHA-256 digest unavailable", e);
    }
  }

  private List<String> compose(String... subcommand) {
    List<String> cmd = new ArrayList<>(List.of("docker", "compose", "--env-file", ".env", "-f", "compose.yaml"));
    cmd.addAll(List.of(subcommand));
    return cmd;
  }

  private void command(Duration timeout, boolean live, String fail, List<String> cmd) {
    require(processes.run(timeout, live, cmd), fail);
  }

  private static void require(CommandRunner.Result result, String fail) {
    if (!result.succeeded()) {
      throw new StartupException(fail + (result.timedOut() ? " (timed out)" : "")
          + "\nLast tool output (redacted):\n" + result.tail());
    }
  }

  private static void sleep(int seconds) {
    try { Thread.sleep(Duration.ofSeconds(seconds)); }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StartupException("Interrupted during startup", e);
    }
  }
}
