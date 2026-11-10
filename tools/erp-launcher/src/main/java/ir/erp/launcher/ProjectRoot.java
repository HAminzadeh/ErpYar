package ir.erp.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Finds the checkout from an explicit nested path or IntelliJ working directory. */
public final class ProjectRoot {
  private ProjectRoot() { }

  public static Path find(Path requested) {
    Path original = requested == null ? Path.of(System.getProperty("user.dir")) : requested;
    Path candidate = original.toAbsolutePath().normalize();
    if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
      throw new StartupException("Repository path is missing, not a directory or is a symlink: " + candidate);
    }
    // Never traverse an explicitly provided symbolic link or Windows junction into another checkout.
    for (Path segment = candidate; segment != null; segment = segment.getParent()) {
      if (Files.isSymbolicLink(segment)) {
        throw new StartupException("Repository path traverses symbolic link: " + segment);
      }
    }
    Path root = candidate;
    while (root != null) {
      if (valid(root)) {
        try {
          // Canonicalize existing directories (not user-controlled string concatenation).
          Path real = root.toRealPath();
          if (!candidate.toRealPath().startsWith(real)) {
            throw new StartupException("Resolved path escaped the repository: " + candidate);
          }
          return real;
        } catch (IOException e) {
          throw new StartupException("Cannot resolve repository path: " + candidate, e);
        }
      }
      root = root.getParent();
    }
    throw new StartupException("Cannot locate ErpYar source root from " + candidate
        + "; expected pom.xml, compose.yaml, services/identity and tools/erp-launcher. "
        + "Set Working directory to $PROJECT_DIR$ or use --repo <nested-or-root-path>.");
  }

  static boolean valid(Path root) {
    return Files.isRegularFile(root.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS)
        && Files.isRegularFile(root.resolve("compose.yaml"), LinkOption.NOFOLLOW_LINKS)
        && Files.isDirectory(root.resolve("services/identity"), LinkOption.NOFOLLOW_LINKS)
        && Files.isDirectory(root.resolve("tools/erp-launcher"), LinkOption.NOFOLLOW_LINKS);
  }
}
