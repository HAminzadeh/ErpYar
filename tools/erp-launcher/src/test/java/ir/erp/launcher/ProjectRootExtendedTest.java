package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRootExtendedTest {
  @TempDir Path scratch;

  private Path repo(String folder) throws Exception {
    Path root = Files.createDirectories(scratch.resolve(folder));
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    Files.writeString(root.resolve("compose.yaml"), "services: {}");
    Files.createDirectories(root.resolve("services/identity"));
    Files.createDirectories(root.resolve("tools/erp-launcher/src/main/java"));
    return root;
  }

  @Test void explicitRepoMayPointToNestedExistingDirectoryWithSpaces() throws Exception {
    Path root = repo("My ERP Workspace");
    assertEquals(root.toRealPath(), ProjectRoot.find(root.resolve("tools/erp-launcher/src/main/java")));
  }

  @Test void missingPathFailsWithoutCreatingAnything() throws Exception {
    Path root = repo("valid");
    Path missing = root.resolve("tools/erp-launcher/does-not-exist");
    assertThrows(StartupException.class, () -> ProjectRoot.find(missing));
    assertFalse(Files.exists(missing));
  }

  @Test void unrelatedFolderWithFakePomIsNotAccepted() throws Exception {
    Path fake = Files.createDirectory(scratch.resolve("invalid"));
    Files.writeString(fake.resolve("pom.xml"), "<project/>");
    assertThrows(StartupException.class, () -> ProjectRoot.find(fake));
  }

  @Test void nestedSymbolicLinkEscapingRepositoryIsRejectedWhenSupported() throws Exception {
    Path root = repo("root");
    Path outside = Files.createDirectory(scratch.resolve("elsewhere"));
    Path link = root.resolve("tools/erp-launcher/src/external");
    try { Files.createSymbolicLink(link, outside); }
    catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) { return; }
    assertThrows(StartupException.class, () -> ProjectRoot.find(link));
  }

  @Test void windowsStyleMissingPathCannotBeMistakenForSourceRoot() {
    if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
    assertThrows(StartupException.class, () -> ProjectRoot.find(Path.of("Z:\\Nonexistent ERP Project\\tools")));
  }
}
