package ir.erp.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Compose file includes infrastructure only; project name retains existing erp-phase1 volume. */
final class InfrastructureComposeRunner {
  static final String CONFIG="infra/compose.infrastructure.yml";
  private final Path root;
  private final DockerClient docker;
  InfrastructureComposeRunner(Path root, DockerClient docker){this.root=root;this.docker=docker;}
  CommandRunner.Result run(Duration timeout,String... args) {
    if (!Files.isRegularFile(root.resolve(CONFIG)))
      throw new StartupException("Missing infrastructure-only Compose file: " + CONFIG);
    List<String> list=new ArrayList<>(List.of("compose","--env-file",".env","-f",CONFIG));
    list.addAll(List.of(args));
    return docker.run(timeout,list);
  }
  void validate() { require(run(Duration.ofSeconds(25),"config","--quiet"),"Invalid infrastructure-only Compose configuration"); }
  void startPostgres() {
    require(run(Duration.ofMinutes(3),"up","-d","--no-deps","--pull","never","postgres"),
        "Could not start PostgreSQL from already verified local Docker image");
  }
  String containerId() {
    CommandRunner.Result r=run(Duration.ofSeconds(20),"ps","-q","postgres");
    require(r,"Cannot inspect PostgreSQL Compose service");
    return r.tail().strip();
  }
  CommandRunner.Result exec(Duration timeout,String shell) {
    return run(timeout,"exec","-T","postgres","sh","-c",shell);
  }
  static void require(CommandRunner.Result r,String error) {
    if(!r.succeeded())throw new StartupException(error+(r.timedOut()?" (timed out)":"")+": "+r.tail());
  }
}
