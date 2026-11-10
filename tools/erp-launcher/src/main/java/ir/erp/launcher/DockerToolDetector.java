package ir.erp.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Docker CLI/Compose/Engine checks. Docker is infrastructure-only in the new design. */
final class DockerToolDetector {
  record DockerStatus(boolean cli, boolean compose, boolean linuxEngine,
                      String cliDetail, String composeDetail, String engineDetail) { }
  private static final Duration SHORT = Duration.ofSeconds(8);
  private DockerToolDetector() { }

  static DockerStatus inspect(CommandRunner runner) {
    CommandRunner.Result cli = runner.run(SHORT, false, List.of("docker", "--version"));
    if (!cli.succeeded()) {
      return new DockerStatus(false, false, false, "Docker CLI not on PATH", "not checked", "not checked");
    }
    CommandRunner.Result compose = runner.run(SHORT, false, List.of("docker", "compose", "version"));
    boolean composeOkay = compose.succeeded() && VersionPolicy.supportsCompose(compose.tail());
    CommandRunner.Result info = runner.run(SHORT, false,
        List.of("docker", "info", "--format", "{{.OSType}}|{{.ServerVersion}}"));
    boolean linux = info.succeeded() && info.tail().strip().startsWith("linux|");
    return new DockerStatus(true, composeOkay, linux,
        "Docker CLI installed", composeOkay ? "Docker Compose v2 available" : "Compose v2 unavailable/unsupported",
        !info.succeeded() ? "Docker Engine not responding" : linux
            ? "Linux Docker Engine available" : "Docker Engine is not running Linux containers");
  }

  /** Only allowed during explicit startup (never Doctor or Plan). No installer invoked. */
  static boolean tryStartExistingDesktop(CommandRunner runner) {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
    Path exe = Path.of("C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe");
    if (!Files.isRegularFile(exe)) return false;
    CommandRunner.Result pre = runner.run(SHORT, false,
        List.of("docker", "info", "--format", "{{.OSType}}"));
    if (pre.succeeded() && pre.tail().strip().equals("linux")) return true;
    try {
      new ProcessBuilder(exe.toString()).start();
      Instant end = Instant.now().plusSeconds(90);
      while (Instant.now().isBefore(end)) {
        CommandRunner.Result ready = runner.run(SHORT, false,
            List.of("docker", "info", "--format", "{{.OSType}}"));
        if (ready.succeeded() && ready.tail().strip().equals("linux")) return true;
        Thread.sleep(3000);
      }
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return false;
  }
}
