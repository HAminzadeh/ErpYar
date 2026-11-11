package ir.erp.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Only existing named Postgres volume is reused; no DOWN, DROP or password reset. */
final class PostgresInfrastructureManager {
  private final Path root;
  private final LocalEnvironment environment;
  private final InfrastructureComposeRunner compose;
  PostgresInfrastructureManager(Path root,LocalEnvironment environment,DockerClient docker) {
    this.root=root;this.environment=environment;this.compose=new InfrastructureComposeRunner(root,docker);
  }
  void start() {
    validateFlywayFiles();
    compose.validate();
    compose.startPostgres();
    new DatabaseConnectionProbe(compose,environment).waitAndAuthenticate();
    new DatabaseProvisioner(compose).ensureDatabases();
  }
  /** Read-only compatibility check. Existing migrations are not rewritten or executed here. */
  private void validateFlywayFiles() {
    for(String module:List.of("identity","organization","master-data","workflow","notification","audit")) {
      Path migration=root.resolve("services").resolve(module).resolve("src/main/resources/db/migration/V1__init.sql");
      if(!Files.isRegularFile(migration)) throw new StartupException("Missing existing Flyway migration: " + migration);
    }
  }
}
