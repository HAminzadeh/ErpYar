package ir.erp.launcher;

import java.time.Duration;
import java.util.List;

/** Logical DB creation only; static names are allow-listed and not interpolated from user data. */
final class DatabaseProvisioner {
  static final List<String> DATABASES=List.of("identity_db","organization_db","master_data_db",
      "workflow_db","notification_db","audit_db");
  private final InfrastructureComposeRunner compose;
  DatabaseProvisioner(InfrastructureComposeRunner compose){this.compose=compose;}
  void ensureDatabases() {
    for(String database:DATABASES) {
      if(exists(database))continue;
      System.out.println("[ErpYar] Provisioning missing database: " + database);
      String command="PGPASSWORD=\"$POSTGRES_PASSWORD\" psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 "
          + "-U erp -d postgres -c 'CREATE DATABASE " + database + "'";
      CommandRunner.Result created=compose.exec(Duration.ofSeconds(25),command);
      if(!created.succeeded() && !exists(database))
        throw new StartupException("Could not create missing database " + database + "; existing databases untouched");
    }
    System.out.println("[ErpYar] All six required logical databases exist; Flyway belongs to active JVM services.");
  }
  private boolean exists(String database) {
    if(!DATABASES.contains(database))throw new StartupException("Unapproved database name");
    String command="PGPASSWORD=\"$POSTGRES_PASSWORD\" psql -X -q -v ON_ERROR_STOP=1 "
        + "-h 127.0.0.1 -U erp -d postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='"+database+"'\"";
    CommandRunner.Result result=compose.exec(Duration.ofSeconds(12),command);
    InfrastructureComposeRunner.require(result,"Could not inspect existing databases");
    String response=result.tail().strip();
    if(response.equals("1"))return true;
    if(response.isBlank())return false;
    throw new StartupException("Unexpected PostgreSQL database-existence response");
  }
}
