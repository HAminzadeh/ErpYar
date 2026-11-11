package ir.erp.launcher;

/** Phase 02 allow-list: Docker exclusively hosts infrastructure, never Java ERP services. */
final class ImageVersionPolicy {
  static final String POSTGRES = "postgres:16-alpine";
  private ImageVersionPolicy() { }
  static void requireAllowed(String imageRef) {
    if (!POSTGRES.equals(imageRef)) throw new StartupException("Image is not an approved infrastructure image");
  }
  static String platform(String os, String architecture) {
    String arch = architecture.trim().toLowerCase();
    if (arch.equals("x86_64") || arch.equals("x86-64")) arch="amd64";
    if (arch.equals("aarch64")) arch="arm64";
    if (!os.equals("linux") || !(arch.equals("amd64") || arch.equals("arm64")))
      throw new StartupException("Unsupported Docker engine platform: " + os + "/" + arch
          + "; use Linux containers with amd64 or arm64 images");
    return os + "/" + arch;
  }
  static String filename(String platform) {
    if (!platform.matches("linux/(amd64|arm64)")) throw new StartupException("Unsupported cache platform");
    return "postgres-16-alpine-" + platform.replace('/', '-') + ".tar";
  }
}
