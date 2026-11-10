package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEnvironmentTest {
  @TempDir Path project;

  @Test void createsSecureEnvironmentAndKeepsItStable() throws Exception {
    LocalEnvironment first = LocalEnvironment.ensure(project);
    String original = Files.readString(project.resolve(".env"));
    assertTrue(first.get("JWT_SECRET").length() >= 32);
    assertTrue(first.get("BOOTSTRAP_ADMIN_PASSWORD").length() >= 16);
    assertEquals("15432", first.get("ERP_DB_HOST_PORT"));
    LocalEnvironment second = LocalEnvironment.ensure(project);
    assertEquals(original, Files.readString(project.resolve(".env")));
    assertEquals(first.get("DB_PASSWORD"), second.get("DB_PASSWORD"));
  }

  @Test void completesMissingFieldsButPreservesCredentials() throws Exception {
    Files.writeString(project.resolve(".env"), "DB_PASSWORD=existing-key\n");
    LocalEnvironment env = LocalEnvironment.ensure(project);
    assertEquals("existing-key", env.get("DB_PASSWORD"));
    assertEquals(6, Files.readAllLines(project.resolve(".env")).stream()
        .filter(x -> x.contains("=")).count());
  }

  @Test void rejectsDuplicateKeys() throws Exception {
    Files.writeString(project.resolve(".env"), "DB_PASSWORD=one\nDB_PASSWORD=two\n");
    assertThrows(StartupException.class, () -> LocalEnvironment.ensure(project));
  }

  @Test void rejectsInvalidExistingSecretsWithoutRotating() throws Exception {
    LocalEnvironment.ensure(project);
    String content = Files.readString(project.resolve(".env"));
    Files.writeString(project.resolve(".env"), content.replaceFirst("JWT_SECRET=[^\\r\\n]+", "JWT_SECRET=short"));
    assertThrows(StartupException.class, () -> LocalEnvironment.ensure(project));
    assertTrue(Files.readString(project.resolve(".env")).contains("JWT_SECRET=short"));
  }

  @Test void findsProjectRootFromNestedModule() throws Exception {
    Files.writeString(project.resolve("pom.xml"), "<project/>");
    Files.writeString(project.resolve("compose.yaml"), "services: {}");
    Files.createDirectories(project.resolve("services/identity"));
    Files.createDirectories(project.resolve("tools/erp-launcher/src"));
    assertTrue(ProjectRoot.valid(project));
    assertEquals(project.toRealPath(), ProjectRoot.find(project.resolve("tools/erp-launcher/src")));
  }

  @Test void checkIsReadOnly() {
    assertThrows(StartupException.class, () -> LocalEnvironment.checkWithoutWriting(project));
    assertFalse(Files.exists(project.resolve(".env")));
  }

  @Test void rejectsInvalidLauncherCombinations() {
    assertThrows(StartupException.class, () -> ErpYarLauncher.Options.parse(new String[] {"--check", "--smoke"}));
    //assertTrue(ErpYarLauncher.Options.parse(new String[] {"--skip-build"}).skipBuild);
    assertTrue(
            ErpYarLauncher.Options
                    .parse(new String[] {"--skip-build"})
                    .skipBuildDeprecated
    );
  }
}
