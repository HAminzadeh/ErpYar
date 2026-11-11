package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresPhase02Test {
  @TempDir Path root;

  @Test void provisionerOnlyCreatesMissingDatabasesAndNeverDropsAnything() throws Exception {
    Files.createDirectories(root.resolve("infra"));
    Files.writeString(root.resolve("infra/compose.infrastructure.yml"),"name: erp-phase1");
    class Fake implements DockerClient {
      final List<String> statements=new ArrayList<>();
      @Override public CommandRunner.Result run(Duration t,List<String> a) {
        String sql=a.getLast();statements.add(sql);
        if(sql.contains("SELECT 1 FROM pg_database"))
          return new CommandRunner.Result(0,false,sql.contains("audit_db")?"":"1");
        if(sql.contains("CREATE DATABASE audit_db"))return new CommandRunner.Result(0,false,"");
        throw new AssertionError("Unexpected command: "+a);
      }
    }
    Fake fake=new Fake();
    new DatabaseProvisioner(new InfrastructureComposeRunner(root,fake)).ensureDatabases();
    assertEquals(1,fake.statements.stream().filter(s->s.contains("CREATE DATABASE")).count());
    assertTrue(fake.statements.stream().noneMatch(s->s.contains("DROP ") || s.contains("ALTER ROLE")));
  }

  @Test void wrongPersistentPasswordIsRejectedWithoutMutation() throws Exception {
    Files.createDirectories(root.resolve("infra"));
    Files.writeString(root.resolve("infra/compose.infrastructure.yml"),"name: erp-phase1");
    LocalEnvironment env=LocalEnvironment.ensure(root);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(env.get("DB_PASSWORD").getBytes(StandardCharsets.UTF_8)));
    class Fake implements DockerClient {
      final List<String> commands=new ArrayList<>();
      @Override public CommandRunner.Result run(Duration t,List<String> a) {
        String shell=a.getLast();commands.add(shell);
        if(shell.contains("pg_isready"))return new CommandRunner.Result(0,false,"ready");
        if(shell.contains("sha256sum"))return new CommandRunner.Result(0,false,hash+"  -");
        if(shell.contains("SELECT 1"))return new CommandRunner.Result(1,false,"FATAL: password authentication failed");
        throw new AssertionError(shell);
      }
    }
    Fake fake=new Fake();
    StartupException e=assertThrows(StartupException.class,
        ()->new DatabaseConnectionProbe(new InfrastructureComposeRunner(root,fake),env).waitAndAuthenticate());
    assertTrue(e.getMessage().contains("28P01"));
    assertTrue(fake.commands.stream().noneMatch(s->s.contains("ALTER ROLE") || s.contains("DROP ")));
  }
}
