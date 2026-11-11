package ir.erp.launcher;

import java.nio.file.Path;
import java.util.List;

/** Phase 02 concrete infrastructure-only bootstrap; independent of Java ERP processes. */
final class InfrastructureManager implements InfrastructureBootstrap {
  private final Path root;
  private final LocalEnvironment environment;
  private final DockerClient docker;
  private final boolean offline;
  InfrastructureManager(Path root,LocalEnvironment env,boolean offline) {
    this(root,env,new DockerProcessClient(root,env.secrets()),offline);
  }
  InfrastructureManager(Path root,LocalEnvironment env,DockerClient docker,boolean offline) {
    this.root=root;this.environment=env;this.docker=docker;this.offline=offline;
  }
  @Override public Preparation describe() {
    return new Preparation(true,List.of(ImageVersionPolicy.POSTGRES),
        "Phase 02: PostgreSQL 16 infrastructure only. Docker image cache and database provisioning implemented. "
        + "Selected Java service startup remains Phase 04.");
  }
  @Override public void startInfrastructure() {
    DockerImageCacheManager cache=new DockerImageCacheManager(root,docker);
    cache.ensureAvailable(offline);
    new PostgresInfrastructureManager(root,environment,docker).start();
  }
}
