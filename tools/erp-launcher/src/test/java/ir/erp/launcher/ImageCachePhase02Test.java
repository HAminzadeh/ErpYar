package ir.erp.launcher;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageCachePhase02Test {
  @TempDir Path root;
  private static final String ID="sha256:"+"a".repeat(64);
  private static final String DIGEST="postgres@sha256:"+"b".repeat(64);

  private static final class FakeDocker implements DockerClient {
    boolean imagePresent=true;
    String architecture="amd64";
    int saves,loads,pulls;
    final List<List<String>> commands=new ArrayList<>();
    @Override public CommandRunner.Result run(Duration timeout,List<String> args) {
      commands.add(List.copyOf(args));
      if(args.equals(List.of("info","--format","{{.OSType}}|{{.Architecture}}"))) return ok("linux|"+architecture);
      if(args.size()>1 && args.get(0).equals("image") && args.get(1).equals("inspect")) {
        if(!imagePresent)return fail("not found");
        if(args.getLast().equals("{{.Id}}|{{.Os}}|{{.Architecture}}"))return ok(ID+"|linux|amd64");
        return ok("[\""+DIGEST+"\"]");
      }
      if(args.size()>1 && args.get(0).equals("image") && args.get(1).equals("save")) {
        saves++;
        try { Files.writeString(Path.of(args.get(3)),"portable image export fixture"); }
        catch(Exception e){throw new RuntimeException(e);}
        return ok("");
      }
      if(args.size()>1 && args.get(0).equals("image") && args.get(1).equals("load")) {
        loads++;imagePresent=true;return ok("Loaded image: postgres:16-alpine");
      }
      if(args.size()>1 && args.get(0).equals("image") && args.get(1).equals("pull")) {
        pulls++;imagePresent=true;return ok("pulled");
      }
      throw new AssertionError("Unexpected Docker command: " + args);
    }
    private static CommandRunner.Result ok(String out){return new CommandRunner.Result(0,false,out);}
    private static CommandRunner.Result fail(String out){return new CommandRunner.Result(1,false,out);}
  }

  @Test void cacheExportIsIdempotentAndRestoresOfflineWithoutPull() {
    FakeDocker fake=new FakeDocker();
    DockerImageCacheManager manager=new DockerImageCacheManager(root,fake);
    manager.ensureAvailable(true);
    assertEquals(1,fake.saves);
    assertEquals(0,fake.pulls);
    ImageManifest.Entry entry=manager.readManifest().find(ImageVersionPolicy.POSTGRES,"linux/amd64");
    assertNotNull(entry);
    assertEquals(DIGEST,entry.repoDigest());
    assertNotNull(ManifestValidator.validatedTar(manager.directory(),entry));
    manager.ensureAvailable(true);
    manager.exportImages();
    assertEquals(1,fake.saves);
    fake.imagePresent=false;
    manager.ensureAvailable(true);
    assertEquals(1,fake.loads);
    assertEquals(0,fake.pulls);
  }

  @Test void corruptedTarFailsBeforeDockerLoad() throws Exception {
    FakeDocker fake=new FakeDocker();DockerImageCacheManager manager=new DockerImageCacheManager(root,fake);
    manager.exportImages();
    Path tar=manager.directory().resolve(ImageVersionPolicy.filename("linux/amd64"));
    Files.writeString(tar,"tamper");
    fake.imagePresent=false;
    assertThrows(StartupException.class,()->manager.importImages());
    assertEquals(0,fake.loads);
  }

  @Test void offlineMissingCacheDoesNotContactRegistry() {
    FakeDocker fake=new FakeDocker();fake.imagePresent=false;
    assertThrows(StartupException.class,()->new DockerImageCacheManager(root,fake).ensureAvailable(true));
    assertEquals(0,fake.pulls);
  }

  @Test void incompatiblePlatformCannotExportOrLoad() {
    FakeDocker fake=new FakeDocker();fake.architecture="arm64";
    assertThrows(StartupException.class,()->new DockerImageCacheManager(root,fake).exportImages());
    assertEquals(0,fake.saves);
  }

  @Test void rejectManifestTraversalAndDuplicateJsonKeys() {
    String now=Instant.now().toString();
    ImageManifest.Entry e=new ImageManifest.Entry(ImageVersionPolicy.POSTGRES,DIGEST,ID,"linux","amd64",
        "linux/amd64","../escape.tar","0".repeat(64),1,now,"existing-local",now);
    assertThrows(StartupException.class,()->ImageManifest.parse(new ImageManifest(List.of(e)).serialize()));
    assertThrows(StartupException.class,()->JsonCodec.parse("{\"schemaVersion\":1,\"schemaVersion\":1,\"images\":[]}"));
  }

  @Test void manifestRoundtripIsStrictAndPlatformNormalized() {
    String now=Instant.now().toString();
    ImageManifest.Entry e=new ImageManifest.Entry(ImageVersionPolicy.POSTGRES,DIGEST,ID,"linux","amd64",
        "linux/amd64",ImageVersionPolicy.filename("linux/amd64"),"c".repeat(64),100,now,"existing-local",now);
    ImageManifest m=ImageManifest.parse(new ImageManifest(List.of(e)).serialize());
    assertEquals(100,m.find(ImageVersionPolicy.POSTGRES,"linux/amd64").sizeBytes());
    assertEquals("linux/amd64",ImageVersionPolicy.platform("linux","x86_64"));
  }
}
