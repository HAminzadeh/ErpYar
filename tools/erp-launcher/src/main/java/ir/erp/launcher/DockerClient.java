package ir.erp.launcher;

import java.time.Duration;
import java.util.List;

/** Injectable command boundary; tests can simulate Docker without a daemon. */
interface DockerClient {
  CommandRunner.Result run(Duration timeout, List<String> args);
}
