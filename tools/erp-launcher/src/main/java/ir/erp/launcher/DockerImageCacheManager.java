package ir.erp.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Offline-first Docker cache for infrastructure images only; all image operations are idempotent. */
final class DockerImageCacheManager {
  private static final Duration SHORT=Duration.ofSeconds(25);
  private static final Duration TRANSFER=Duration.ofMinutes(20);
  private static final String IMAGE=ImageVersionPolicy.POSTGRES;
  private final Path root;
  private final Path directory;
  private final DockerClient docker;

  DockerImageCacheManager(Path root, DockerClient docker) {
    this.root=root;
    this.directory=root.resolve(".local/docker-images").toAbsolutePath().normalize();
    this.docker=docker;
  }
  Path directory() { return directory; }

  /** Order: local image matching lock -> verified archive -> (online only) trusted registry -> cache. */
  void ensureAvailable(boolean offline) {
    String platform=hostPlatform();
    ImageManifest m=readManifest();
    ImageManifest.Entry entry=m.find(IMAGE,platform);
    ImageInfo current=inspectImage();
    if(entry!=null) {
      // Verify archive even when Docker has the image, so corruption cannot go unnoticed.
      ManifestValidator.validatedTar(directory,entry);
      if (!current.missing() && current.id().equals(entry.imageId()) && current.platform().equals(platform)) {
        verifyDigestIfPresent(current,entry);
        System.out.println("[ErpYar] Approved infrastructure image already in Docker and cache: " + IMAGE);
        return;
      }
      importEntry(entry);
      return;
    }
    if(!current.missing()) {
      requirePlatform(current,platform);
      exportCurrent(platform,"existing-local");
      return;
    }
    if(offline) throw new StartupException("OFFLINE: required image "+IMAGE+" is absent from Docker and verified local cache. "
        + "Copy .local/docker-images from a compatible machine or run --cache-export there.");
    System.out.println("[ErpYar] Pulling approved infrastructure image once: " + IMAGE);
    required(docker.run(TRANSFER,List.of("image","pull",IMAGE)),"Could not pull approved PostgreSQL image");
    ImageInfo pulled=inspectImage();
    requirePlatform(pulled,platform);
    exportCurrent(platform,"registry-pull");
  }

  void exportImages() {
    String platform=hostPlatform();
    ImageInfo current=inspectImage();
    if(current.missing()) throw new StartupException("Required infrastructure image is not in Docker. "
        + "Start --infra-only online first, or download the approved image explicitly.");
    requirePlatform(current,platform);
    ImageManifest.Entry locked=readManifest().find(IMAGE,platform);
    if(locked!=null) {
      ManifestValidator.validatedTar(directory,locked);
      if(!locked.imageId().equals(current.id())) throw new StartupException("Docker image differs from cached known-good image. "
          + "Retain existing cache and review version change before exporting.");
      System.out.println("[ErpYar] Image cache already verified; export skipped (no rewrite).");
      return;
    }
    exportCurrent(platform,"existing-local");
  }

  void importImages() {
    String platform=hostPlatform();
    ImageManifest.Entry e=readManifest().find(IMAGE,platform);
    if(e==null) throw new StartupException("No compatible PostgreSQL image recorded in cache manifest for " + platform);
    // Validation happens before even looking at Docker / invoking load.
    ManifestValidator.validatedTar(directory,e);
    ImageInfo actual=inspectImage();
    if(!actual.missing() && actual.id().equals(e.imageId()) && actual.platform().equals(platform)) {
      System.out.println("[ErpYar] Required image already imported. No docker load needed."); return;
    }
    importEntry(e);
  }

  private void importEntry(ImageManifest.Entry entry) {
    Path tar=ManifestValidator.validatedTar(directory,entry);
    System.out.println("[ErpYar] Importing verified image archive: " + tar.getFileName());
    required(docker.run(TRANSFER,List.of("image","load","-i",tar.toString())),"docker image load failed");
    ImageInfo actual=inspectImage();
    if(actual.missing() || !actual.id().equals(entry.imageId()) || !actual.platform().equals(entry.platform()))
      throw new StartupException("docker load finished but the resulting image ID/platform differs from manifest");
    verifyDigestIfPresent(actual,entry);
    System.out.println("[ErpYar] Offline Docker image restored with exact expected ID.");
  }

  /** Docker save/load does not guarantee that RepoDigests survive retagging.
   *  When Docker supplies a digest, however, a conflicting digest is a hard failure. */
  private static void verifyDigestIfPresent(ImageInfo actual,ImageManifest.Entry locked) {
    if(!locked.repoDigest().isBlank() && !actual.repoDigest().isBlank()
        && !locked.repoDigest().equals(actual.repoDigest()))
      throw new StartupException("Registry RepoDigest conflicts with cache manifest; do not use this image");
  }

  private void exportCurrent(String platform,String source) {
    ImageInfo current=inspectImage();requirePlatform(current,platform);
    ensureCacheDirectory();
    Path tar=directory.resolve(ImageVersionPolicy.filename(platform));
    if(Files.exists(tar,LinkOption.NOFOLLOW_LINKS))
      throw new StartupException("Unregistered cache TAR already exists; do not overwrite: " + tar);
    Path temporary=null;
    try {
      long usable=Files.getFileStore(directory).getUsableSpace();
      if(usable<256L*1024*1024) throw new StartupException("At least 256 MiB free space needed before exporting Docker image");
      temporary=Files.createTempFile(directory,".image-export-",".tmp");
      required(docker.run(TRANSFER,List.of("image","save","-o",temporary.toString(),IMAGE)),"docker image save failed");
      long size=Files.size(temporary);
      if(size<=0)throw new StartupException("Docker image save produced an empty archive");
      String hash=ManifestValidator.sha256(temporary);
      // Only after successful export and hash, atomically publish archive and lock.
      Files.move(temporary,tar,StandardCopyOption.ATOMIC_MOVE);
      temporary=null;
      String now=Instant.now().toString();
      ImageManifest.Entry e=new ImageManifest.Entry(IMAGE,current.repoDigest(),current.id(),
          current.os(),current.arch(),platform,tar.getFileName().toString(),hash,size,now,source,now);
      ManifestValidator.validatedTar(directory,e);
      ImageManifest updated=readManifest().replace(e);
      writeManifest(updated);
      System.out.println("[ErpYar] Cached " + IMAGE + " at " + tar + " (" + (size/1024/1024) + " MiB, SHA-256=" + hash + ")");
    } catch(IOException e) { throw new StartupException("Image cache write failed; verify free space and filesystem support",e); }
    finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ignored) {} }
  }

  ImageManifest readManifest() {
    Path manifest=directory.resolve("manifest.json");
    if(Files.isSymbolicLink(directory) || Files.isSymbolicLink(directory.getParent()) || Files.isSymbolicLink(manifest))
      throw new StartupException("Refusing symbolic-link Docker cache or manifest");
    if(!Files.exists(manifest,LinkOption.NOFOLLOW_LINKS)) return ImageManifest.empty();
    try { return ImageManifest.parse(Files.readString(manifest,StandardCharsets.UTF_8)); }
    catch(IOException e) { throw new StartupException("Cannot read Docker image cache manifest",e); }
  }
  private void writeManifest(ImageManifest manifest) {
    ensureCacheDirectory();Path tmp=null;
    try {
      tmp=Files.createTempFile(directory,".manifest-",".tmp");
      Files.writeString(tmp,manifest.serialize(),StandardCharsets.UTF_8);
      Files.move(tmp,directory.resolve("manifest.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      tmp=null;
    } catch(IOException e) { throw new StartupException("Cannot atomically save Docker image cache manifest",e); }
    finally {if(tmp!=null)try {Files.deleteIfExists(tmp);}catch(IOException ignored){}}
  }
  private void ensureCacheDirectory() {
    Path local=root.resolve(".local");
    if(Files.isSymbolicLink(local) || Files.isSymbolicLink(directory))
      throw new StartupException("Refusing symlink/junction cache directory");
    try { Files.createDirectories(directory); } catch(IOException e){throw new StartupException("Cannot create Docker image cache directory",e);}
  }
  record ImageInfo(String id,String os,String arch,String repoDigest) {
    static ImageInfo absent(){ return new ImageInfo("","","",""); }
    boolean missing(){return id.isBlank();}
    String platform(){return missing()?"":ImageVersionPolicy.platform(os,arch);}
  }
  ImageInfo inspectImage() {
    CommandRunner.Result r=docker.run(SHORT,List.of("image","inspect",IMAGE,"--format","{{.Id}}|{{.Os}}|{{.Architecture}}"));
    if(!r.succeeded()) return ImageInfo.absent();
    String[] p=r.tail().strip().split("\\|",-1);
    if(p.length!=3 || !p[0].matches("sha256:[0-9a-f]{64}")) throw new StartupException("Invalid Docker image inspection output");
    String digest="";
    CommandRunner.Result d=docker.run(SHORT,List.of("image","inspect",IMAGE,"--format","{{json .RepoDigests}}"));
    if(d.succeeded()) {
      java.util.regex.Matcher m=Pattern.compile("([^\\\"\\s]+@sha256:[0-9a-f]{64})").matcher(d.tail());
      if(m.find()) digest=m.group(1);
    }
    return new ImageInfo(p[0],p[1],p[2],digest);
  }
  String hostPlatform() {
    CommandRunner.Result r=docker.run(SHORT,List.of("info","--format","{{.OSType}}|{{.Architecture}}"));
    required(r,"Docker Engine unavailable; start Docker Desktop with Linux containers");
    String[] parts=r.tail().strip().split("\\|",-1);
    if(parts.length!=2)throw new StartupException("Docker Engine returned invalid platform information");
    return ImageVersionPolicy.platform(parts[0],parts[1]);
  }
  private static void requirePlatform(ImageInfo i,String expected) {
    if(i.missing())throw new StartupException("Required Docker infrastructure image missing");
    if(!i.platform().equals(expected))throw new StartupException("Docker image platform "+i.platform()
        + " differs from engine platform " + expected);
  }
  private static void required(CommandRunner.Result r,String message) {
    if(!r.succeeded()) throw new StartupException(message+(r.timedOut()?" (timed out)":"")+": "+r.tail());
  }
}
