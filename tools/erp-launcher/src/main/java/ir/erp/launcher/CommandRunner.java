package ir.erp.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Executes installed local tools only: never downloads and executes scripts. */
final class CommandRunner {
  private final Path root;
  private final Set<String> secrets;

  CommandRunner(Path root, Set<String> secrets) {
    this.root = root;
    this.secrets = secrets;
  }

  Result run(Duration timeout, boolean live, List<String> command) {
    return run(timeout, live, command, Map.of());
  }

  Result run(Duration timeout, boolean live, List<String> command, Map<String, String> extras) {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(root.toFile());
    builder.redirectErrorStream(true);
    // Prevent inherited shell variables silently overriding the verified .env in Compose.
    Map<String, String> env = builder.environment();
    for (String key : List.of("JWT_SECRET", "DB_PASSWORD", "BOOTSTRAP_ADMIN_PASSWORD",
        "BOOTSTRAP_ADMIN_USERNAME", "GATEWAY_PORT", "ERP_DB_HOST_PORT", "COMPOSE_FILE", "COMPOSE_PROFILES")) {
      env.remove(key);
    }
    env.putAll(extras);
    try {
      Process process = builder.start();
      ArrayDeque<String> output = new ArrayDeque<>();
      Thread collector = Thread.startVirtualThread(() -> {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            String cleaned = redact(line);
            synchronized (output) {
              if (output.size() == 100) output.removeFirst();
              output.addLast(cleaned);
            }
            if (live) System.out.println("[tool] " + cleaned);
          }
        } catch (IOException e) {
          synchronized (output) { output.addLast("[output unavailable]"); }
        }
      });
      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
      collector.join(5_000);
      List<String> last;
      synchronized (output) { last = new ArrayList<>(output); }
      return new Result(finished ? process.exitValue() : -1, !finished, String.join("\n", last));
    } catch (IOException e) {
      return new Result(-1, false, "Cannot run " + command.getFirst()
          + ": executable missing or inaccessible (" + e.getClass().getSimpleName() + ")");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StartupException("Interrupted while running " + command.getFirst(), e);
    }
  }

  private String redact(String line) {
    String cleaned = line;
    for (String secret : secrets) {
      if (secret != null && !secret.isEmpty()) cleaned = cleaned.replace(secret, "[REDACTED]");
    }
    return cleaned;
  }

  record Result(int exitCode, boolean timedOut, String tail) {
    boolean succeeded() { return exitCode == 0 && !timedOut; }
  }
}
