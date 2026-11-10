package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapPhase01Test {
  @TempDir Path scratch;

  private Path repo() throws Exception {
    Path root = Files.createDirectory(scratch.resolve("root with spaces"));
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    Files.writeString(root.resolve("compose.yaml"), "services: {}");
    Files.createDirectories(root.resolve("services/identity"));
    Files.createDirectories(root.resolve("tools/erp-launcher"));
    return root;
  }

  @Test void doctorIsReadOnlyAndReportsRealPrerequisites() throws Exception {
    Path root = repo();
    StartupOrchestrator.Execution first = new StartupOrchestrator(root,
        ErpYarLauncher.Options.parse(new String[] {"--doctor", "--json"})).execute();
    StartupOrchestrator.Execution second = new StartupOrchestrator(root,
        ErpYarLauncher.Options.parse(new String[] {"--check"})).execute();
    assertFalse(Files.exists(root.resolve(".env")));
    assertFalse(Files.exists(root.resolve(".local")));
    assertTrue(first.report().toJson().contains("\"mode\":\"DOCTOR\""));
    assertTrue(first.report().toJson().contains("\"stage\":\"PREFLIGHT\""));
    assertEquals(first.report().checks().stream().filter(c -> c.id().equals("java")).count(), 1);
    assertEquals(second.report().checks().stream().filter(c -> c.id().equals("java")).count(), 1);
  }

  @Test void environmentOnlyIsIdempotentAndDoesNotInvokeDocker() throws Exception {
    Path root = repo();
    ErpYarLauncher.Options options = ErpYarLauncher.Options.parse(new String[] {"--env-only", "--json"});
    assertEquals(0, new StartupOrchestrator(root, options).execute().exitCode());
    String content = Files.readString(root.resolve(".env"));
    assertEquals(0, new StartupOrchestrator(root, options).execute().exitCode());
    assertEquals(content, Files.readString(root.resolve(".env")));
    assertFalse(Files.exists(root.resolve(".local")));
  }

  @Test void planIsExplicitlyDeferredAndNeverStartsServices() throws Exception {
    Path root = repo();
    var result = new StartupOrchestrator(root,
        ErpYarLauncher.Options.parse(new String[] {"--plan"})).execute();
    assertTrue(result.report().checks().stream().anyMatch(c ->
        c.stage() == StartupReport.Stage.INFRA_PREP && c.result() == StartupReport.Result.DEFERRED));
    assertTrue(result.report().checks().stream().anyMatch(c ->
        c.stage() == StartupReport.Stage.SERVICE_PLAN && c.result() == StartupReport.Result.DEFERRED));
    assertFalse(Files.exists(root.resolve(".env")));
  }

  @Test void legacyDockerOrchestratorCannotLaunchErpJavaContainers() throws Exception {
    Path root = repo();
    var old = new ComposeOrchestrator(root, null, ErpYarLauncher.Options.parse(new String[0]));
    assertThrows(StartupException.class, old::start);
    assertThrows(StartupException.class, old::runSmokeTest);
    assertThrows(StartupException.class, () -> new InfrastructureManager().startInfrastructure());
    assertThrows(StartupException.class, () -> new JavaServiceRuntime().startSelected(List.of("gateway")));
  }

  @Test void jsonEscapesQuotesAndNeverNeedsCredentials() {
    var report = new StartupReport("DOCTOR");
    report.add(StartupReport.Stage.PREFLIGHT, "a", StartupReport.Level.INFO,
        StartupReport.Result.PASS, "quote: \" okay", "", 1);
    report.outcome("DIAGNOSTIC_COMPLETE");
    String json = report.toJson();
    assertTrue(json.contains("quote: \\\" okay"));
    assertTrue(json.contains("\"durationMs\":1"));
  }

  @Test void versionsAndFlagsHaveExplicitContracts() {
    assertTrue(VersionPolicy.supportsMaven("Apache Maven 3.9.9"));
    assertFalse(VersionPolicy.supportsMaven("Apache Maven 3.8.8"));
    assertTrue(VersionPolicy.supportsCompose("Docker Compose version v2.40.0"));
    assertFalse(VersionPolicy.supportsCompose("docker-compose version 1.29.2"));
    assertThrows(StartupException.class,
        () -> ErpYarLauncher.Options.parse(new String[] {"--doctor", "--start"}));
    assertThrows(StartupException.class,
        () -> ErpYarLauncher.Options.parse(new String[] {"--smoke"}));
    assertThrows(StartupException.class, () -> ToolInstaller.refuseUnapprovedChange(false));
  }
}
