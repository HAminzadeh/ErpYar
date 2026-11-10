package ir.erp.launcher;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the actual Maven and Compose checkout, not the current Downloads folder. */
public final class ProjectRoot {
  private ProjectRoot() { }

  public static Path find(Path requested) {
    Path candidate = requested == null ? Path.of(System.getProperty("user.dir")) : requested;
    candidate = candidate.toAbsolutePath().normalize();
    if (requested != null) {
      if (valid(candidate)) return candidate;
      throw new StartupException("Invalid --repo path: " + candidate
          + " (expected pom.xml, compose.yaml, services/identity and tools/erp-launcher).");
    }
    while (candidate != null) {
      if (valid(candidate)) return candidate;
      candidate = candidate.getParent();
    }
    throw new StartupException("Cannot locate ErpYar source root. Set IntelliJ Working directory to "
        + "$PROJECT_DIR$ or pass --repo D:\\ChatGPT_Projects\\ErpYar.");
  }

  static boolean valid(Path root) {
    return Files.isRegularFile(root.resolve("pom.xml"))
        && Files.isRegularFile(root.resolve("compose.yaml"))
        && Files.isDirectory(root.resolve("services/identity"))
        && Files.isDirectory(root.resolve("tools/erp-launcher"));
  }
}
