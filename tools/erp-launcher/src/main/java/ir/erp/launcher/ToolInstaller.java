package ir.erp.launcher;

import java.util.Map;

/** Approval-first installer policy: phase 01 deliberately performs no privileged changes. */
final class ToolInstaller {
  record Recommendation(String tool, String publisher, String verifiedSource,
                        String action, boolean needsExplicitApproval) { }
  private static final Map<String, Recommendation> RECOMMENDATIONS = Map.of(
      "docker", new Recommendation("Docker Desktop", "Docker Inc.",
          "https://docs.docker.com/desktop/setup/install/windows-install/",
          "Install Docker Desktop after verifying Windows/WSL 2 and accepting required permissions.", true),
      "java", new Recommendation("JDK 21", "Eclipse Adoptium",
          "https://adoptium.net/temurin/releases/?version=21",
          "Install Temurin JDK 21, then set IntelliJ Project SDK and Maven Runner JRE to 21.", true),
      "maven", new Recommendation("Apache Maven 3.9+", "Apache Software Foundation",
          "https://maven.apache.org/download.cgi",
          "Use IntelliJ bundled Maven 3.9+ or the official Maven distribution.", true));
  private ToolInstaller() { }
  static Recommendation forTool(String tool) { return RECOMMENDATIONS.get(tool); }
  static void refuseUnapprovedChange(boolean approved) {
    if (!approved) throw new StartupException("System installation requires explicit operator approval. "
        + "Phase 01 runs read-only diagnostics and provides trusted installation instructions.");
    // Phase 01 does not impersonate a package manager or initiate silent installations.
    throw new StartupException("Automated system installation is not supported safely in this phase; "
        + "follow the verified publisher instructions and rerun Doctor.");
  }
}
