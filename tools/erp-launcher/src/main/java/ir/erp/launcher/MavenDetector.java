package ir.erp.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detects external or IntelliJ's bundled Maven without modifying PATH/JAVA_HOME. */
final class MavenDetector {
  record MavenStatus(boolean found, boolean versionSupported, String description, String action) { }
  private MavenDetector() { }

  static MavenStatus inspect(Path root, CommandRunner runner) {
    List<List<String>> commands = new ArrayList<>();
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    commands.add(List.of(windows ? "mvn.cmd" : "mvn", "--version"));
    Path wrapper = root.resolve(windows ? "mvnw.cmd" : "mvnw");
    if (Files.isRegularFile(wrapper)) commands.add(List.of(wrapper.toString(), "--version"));
    for (Path dir : ideaHomes()) {
      Path bundled = dir.resolve("plugins/maven/lib/maven3/bin")
          .resolve(windows ? "mvn.cmd" : "mvn");
      if (Files.isRegularFile(bundled)) commands.add(List.of(bundled.toString(), "--version"));
    }
    boolean tooOld = false;
    for (List<String> command : commands) {
      CommandRunner.Result result = runner.run(Duration.ofSeconds(12), false, command);
      if (result.succeeded()) {
        if (VersionPolicy.supportsMaven(result.tail())) {
          return new MavenStatus(true, true, "Maven 3.9+ detected (" + command.getFirst() + ")", "");
        }
        tooOld = true;
      }
    }
    if (tooOld) return new MavenStatus(true, false, "Maven is below 3.9 or version is unknown",
        "Set Maven 3.9+ in IntelliJ Settings > Build Tools > Maven; select Project JDK 21 for Maven Runner.");
    return new MavenStatus(false, false, "Maven CLI not detected; IntelliJ may still provide bundled Maven",
        "Use bundled Maven in IntelliJ or install Apache Maven 3.9+ from maven.apache.org; do not change global Java 8 installations.");
  }

  static String cacheHint(Path root) {
    // Cache inspection is read-only; it does not promise that every transitive JAR is present.
    String boot = "4.0.8";
    try {
      String pom = Files.readString(root.resolve("pom.xml"));
      var parentBlock = java.util.regex.Pattern.compile("<parent>(.*?)</parent>",
          java.util.regex.Pattern.DOTALL).matcher(pom);
      if (parentBlock.find()) {
        var version = java.util.regex.Pattern.compile("<version>\\s*([^<]+)</version>")
            .matcher(parentBlock.group(1));
        if (version.find()) boot = version.group(1).trim();
      }
    } catch (Exception ignored) { /* Read-only best-effort version inspection. */ }
    Path parent = Path.of(System.getProperty("user.home"), ".m2", "repository",
        "org", "springframework", "boot", "spring-boot-starter-parent", boot,
        "spring-boot-starter-parent-" + boot + ".pom");
    if (Files.isRegularFile(parent))
      return "Cached Spring Boot parent " + boot + " detected; transitive dependencies not yet verified";
    return "Spring Boot parent " + boot + " not detected in default Maven cache (custom repository may be configured)";
  }

  static String mirrorHint() {
    Path settings = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
    if (!Files.isRegularFile(settings)) return "";
    try {
      String xml = Files.readString(settings);
      if (xml.contains("<mirror>") || xml.contains("<mirrors>")) {
        return "Maven settings.xml contains a repository mirror. If dependency resolution fails, "
            + "distinguish a private Nexus/Artifactory outage from source-code compilation errors.";
      }
    } catch (Exception ignored) { return "Maven settings.xml exists but could not be inspected."; }
    return "";
  }

  private static List<Path> ideaHomes() {
    List<Path> out = new ArrayList<>();
    String property = System.getProperty("idea.home.path", "");
    if (!property.isBlank()) out.add(Path.of(property));
    String env = System.getenv("IDEA_HOME");
    if (env != null && !env.isBlank()) out.add(Path.of(env));
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      Path parent = Path.of("C:\\Program Files\\JetBrains");
      if (Files.isDirectory(parent)) {
        try (var listing = Files.list(parent)) {
          listing.filter(p -> p.getFileName().toString().startsWith("IntelliJ IDEA"))
              .limit(12).forEach(out::add);
        } catch (Exception ignored) { /* Detection only. */ }
      }
    }
    return out;
  }
}
