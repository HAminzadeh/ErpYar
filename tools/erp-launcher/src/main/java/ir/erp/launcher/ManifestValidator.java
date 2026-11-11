package ir.erp.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Reject corrupt archives and path traversal before invoking docker load. */
final class ManifestValidator {
  private ManifestValidator() { }
  static void validateEntry(ImageManifest.Entry e) {
    ImageVersionPolicy.requireAllowed(e.imageRef());
    if (!e.imageId().matches("sha256:[0-9a-f]{64}")) throw new StartupException("Invalid Docker image ID in manifest");
    if (!(e.repoDigest().isEmpty() || e.repoDigest().matches("[^\\s]+@sha256:[0-9a-f]{64}")))
      throw new StartupException("Invalid repository digest in manifest");
    if (!e.platform().equals(ImageVersionPolicy.platform(e.os(),e.architecture())))
      throw new StartupException("Manifest image platform and architecture disagree");
    if (!e.tarFile().equals(ImageVersionPolicy.filename(e.platform())))
      throw new StartupException("Invalid cache TAR name for selected platform");
    if (!e.sha256().matches("[0-9a-f]{64}")) throw new StartupException("Invalid TAR SHA-256 in manifest");
    if (e.sizeBytes() <= 0 || e.sizeBytes()>50L*1024*1024*1024)
      throw new StartupException("TAR size outside safe bounds");
    if (!e.source().matches("existing-local|registry-pull")) throw new StartupException("Invalid cache image source");
    try { Instant.parse(e.createdAt()); Instant.parse(e.verifiedAt()); }
    catch(Exception x) { throw new StartupException("Invalid manifest timestamps"); }
  }
  static Path validatedTar(Path cacheDir, ImageManifest.Entry e) {
    validateEntry(e);
    if (Files.isSymbolicLink(cacheDir) || Files.isSymbolicLink(cacheDir.getParent()))
      throw new StartupException("Refusing symbolic-link cache directories");
    Path tar=cacheDir.resolve(e.tarFile()).normalize();
    if (!tar.getParent().equals(cacheDir.toAbsolutePath().normalize()) || Files.isSymbolicLink(tar))
      throw new StartupException("Unsafe cache TAR path");
    try {
      if (!Files.isRegularFile(tar,LinkOption.NOFOLLOW_LINKS)) throw new StartupException("Docker image cache TAR missing: " + tar);
      if (Files.size(tar)!=e.sizeBytes()) throw new StartupException("Cache TAR file size mismatch");
      if (!sha256(tar).equals(e.sha256())) throw new StartupException("Cache TAR SHA-256 mismatch; refuse docker load");
    } catch(IOException ex) { throw new StartupException("Cannot verify image TAR",ex); }
    return tar;
  }
  static String sha256(Path path) {
    try {
      MessageDigest md=MessageDigest.getInstance("SHA-256");
      try (InputStream in=Files.newInputStream(path)) {
        byte[] buf=new byte[1<<16];int n;while((n=in.read(buf))!=-1)md.update(buf,0,n);
      }
      return HexFormat.of().formatHex(md.digest());
    } catch(IOException | NoSuchAlgorithmException e) { throw new StartupException("Cannot hash image TAR",e); }
  }
}
