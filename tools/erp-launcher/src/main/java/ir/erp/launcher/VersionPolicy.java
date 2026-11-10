package ir.erp.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pinned development runtime requirements; never changes workstation tool versions. */
final class VersionPolicy {
  static final int JAVA_FEATURE = 21;
  static final int MAVEN_MIN_MAJOR = 3;
  static final int MAVEN_MIN_MINOR = 9;
  static final int COMPOSE_MAJOR = 2;
  private static final Pattern VERSION = Pattern.compile("(?:^|\\s)v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

  private VersionPolicy() { }
  static int[] parse(String value) {
    Matcher m = VERSION.matcher(value == null ? "" : value);
    if (!m.find()) return new int[0];
    return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
        m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
  }
  static boolean supportsMaven(String versionOutput) {
    int[] v = parse(versionOutput);
    return v.length > 0 && (v[0] > MAVEN_MIN_MAJOR ||
        (v[0] == MAVEN_MIN_MAJOR && v[1] >= MAVEN_MIN_MINOR));
  }
  static boolean supportsCompose(String versionOutput) {
    int[] v = parse(versionOutput);
    return v.length > 0 && v[0] >= COMPOSE_MAJOR;
  }
}
