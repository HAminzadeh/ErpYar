package ir.erp.launcher;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Confirm env agreement AND password-based TCP authentication, not just pg_isready. */
final class DatabaseConnectionProbe {
  private final InfrastructureComposeRunner compose;
  private final LocalEnvironment env;
  DatabaseConnectionProbe(InfrastructureComposeRunner compose,LocalEnvironment env){this.compose=compose;this.env=env;}
  void waitAndAuthenticate() {
    Instant until=Instant.now().plus(Duration.ofSeconds(100));
    while(Instant.now().isBefore(until)) {
      CommandRunner.Result ready=compose.exec(Duration.ofSeconds(8),"pg_isready -U erp -d postgres");
      if(ready.succeeded())break;
      pause();
    }
    CommandRunner.Result ready=compose.exec(Duration.ofSeconds(10),"pg_isready -U erp -d postgres");
    if(!ready.succeeded())throw new StartupException("PostgreSQL did not become ready; inspect docker compose logs postgres; volumes untouched");

    // Verify the environment of the existing container, without including passwords in argv.
    CommandRunner.Result inside=compose.exec(Duration.ofSeconds(12),"printf '%s' \"$POSTGRES_PASSWORD\" | sha256sum");
    if(!inside.succeeded() || !inside.tail().strip().matches("[0-9a-f]{64}\\s+.*"))
      throw new StartupException("Cannot inspect PostgreSQL container environment password hash");
    String expected=sha256(env.get("DB_PASSWORD"));
    String actual=inside.tail().strip().substring(0,64);
    if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII)))
      throw new StartupException("PostgreSQL container POSTGRES_PASSWORD differs from .env. "
          + "Refusing credential rotation; reconcile credentials after a backup.");
    CommandRunner.Result auth=compose.exec(Duration.ofSeconds(15),
        "PGPASSWORD=\"$POSTGRES_PASSWORD\" psql -X -q -h 127.0.0.1 -U erp -d postgres -tAc 'SELECT 1'");
    if(!auth.succeeded() || !auth.tail().strip().equals("1"))
      throw new StartupException("SQLSTATE 28P01 or PostgreSQL authentication failure: "
          + "persistent volume may use an older password. No password/volume changed. "
          + "Make a backup, then perform a controlled credential reconciliation.");
    // The host-side port must be available for IDE connections. TCP probe never sends credentials.
    int port=Integer.parseInt(env.get("ERP_DB_HOST_PORT"));
    try(Socket socket=new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1",port),2500);
    } catch(Exception e) {
      throw new StartupException("PostgreSQL authenticated in Docker but host 127.0.0.1:"+port
          +" is unreachable. Check Docker loopback port binding and other local PostgreSQL servers.");
    }
    System.out.println("[ErpYar] PostgreSQL role erp authenticated over TCP; IDE loopback port is reachable.");
  }
  private static String sha256(String text) {
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
    catch(NoSuchAlgorithmException e) {throw new StartupException("SHA-256 unavailable",e);}
  }
  private static void pause() {
    try {Thread.sleep(2000);} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new StartupException("Interrupted while waiting for PostgreSQL",e);}
  }
}
