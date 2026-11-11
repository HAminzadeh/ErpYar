package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Phase 02.1: exactly one public entry class and no behavior lost with IDE preset removal. */
class UnifiedEntryPointTest {
  @Test void noArgRunAndExplicitStartUseSameOrchestratorMode() {
    var defaultRun = ErpYarLauncher.Options.parse(new String[0]);
    var explicitRun = ErpYarLauncher.Options.parse(new String[] {"--start"});
    assertTrue(defaultRun.start);
    assertTrue(explicitRun.start);
    assertEquals("START", defaultRun.mode());
    assertEquals(defaultRun.mode(), explicitRun.mode());
  }

  @Test void specialTasksRemainModesOfSameClass() {
    assertEquals("DOCTOR", ErpYarLauncher.Options.parse(new String[] {"--doctor"}).mode());
    assertEquals("ENV_ONLY", ErpYarLauncher.Options.parse(new String[] {"--env-only"}).mode());
    assertEquals("CACHE_EXPORT", ErpYarLauncher.Options.parse(new String[] {"--cache-export"}).mode());
    assertEquals("CACHE_IMPORT", ErpYarLauncher.Options.parse(new String[] {"--cache-import", "--offline"}).mode());
    assertTrue(ErpYarLauncher.Options.parse(new String[] {"--cache-import", "--offline"}).offline);
    assertEquals("INFRA_ONLY", ErpYarLauncher.Options.parse(new String[] {"--infra-only"}).mode());
    assertThrows(StartupException.class,
        () -> ErpYarLauncher.Options.parse(new String[] {"--start", "--cache-export"}));
  }

  @Test void smokeTestCannotBeStartedImplicitly() {
    assertThrows(StartupException.class,
        () -> ErpYarLauncher.Options.parse(new String[] {"--smoke"}));
  }
}
