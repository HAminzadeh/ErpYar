package ir.erp.launcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Runs only Docker CLI; no shell expansion and no secret values in argv. */
final class DockerProcessClient implements DockerClient {
  private final CommandRunner process;
  DockerProcessClient(Path root, Set<String> secrets) { process = new CommandRunner(root, secrets); }
  @Override public CommandRunner.Result run(Duration timeout, List<String> args) {
    List<String> command = new ArrayList<>();
    command.add("docker");
    command.addAll(args);
    return process.run(timeout, false, command);
  }
}
