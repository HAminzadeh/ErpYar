package ir.erp.launcher;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Java 21 entry point: infrastructure-only Docker, Java service startup follows in Phase 04. */
public final class ErpYarLauncher {
  private ErpYarLauncher() { }
  public static void main(String[] args) {
    try {
      Options options=Options.parse(args);
      if(options.help){printHelp();return;}
      Path root=ProjectRoot.find(options.repoPath);
      StartupOrchestrator.Execution result=new StartupOrchestrator(root,options).execute();
      System.out.println(options.json?result.report().toJson():result.report().toConsole());
      if(result.exitCode()!=0)System.exit(result.exitCode());
    } catch(StartupException ex) {
      System.err.println("[ErpYar] STARTUP FAILED: " + ex.getMessage());
      System.exit(2);
    } catch(Exception ex) {
      System.err.println("[ErpYar] Unexpected failure: " + ex.getClass().getSimpleName());
      System.exit(2);
    }
  }
  static void printHelp() {
    System.out.println("ErpYar Phase 02 infrastructure bootstrap (Java 21)");
    System.out.println("  --repo <path>          Checkout root or existing nested directory");
    System.out.println("  --doctor | --check    Read-only preflight; no files/containers altered");
    System.out.println("  --env-only            Create/validate .env without changing existing values");
    System.out.println("  --plan                Read-only implementation and infrastructure plan");
    System.out.println("  --infra-only          Bootstrap PostgreSQL only; do not launch Java services");
    System.out.println("  --cache-export        Save existing PostgreSQL Docker image as verified TAR");
    System.out.println("  --cache-import        Restore PostgreSQL image from verified TAR; no registry contact");
    System.out.println("  --offline             Never pull images; use Docker/local verified cache only");
    System.out.println("  --start               Bootstrap infra, then report Java services DEFERRED until Phase 04");
    System.out.println("  --no-docker-desktop   Do not start already installed Docker Desktop");
    System.out.println("  --json                Machine-readable startup report (no credentials)");
    System.out.println("  --help                Show options");
    System.out.println("Exit: 0 successful requested infrastructure operation, 2 failure, 3 Java startup deferred.");
  }
  static final class Options {
    Path repoPath;
    boolean doctor,envOnly,plan,start,startDockerDesktop=true,json,help,skipBuildDeprecated;
    boolean infraOnly,cacheExport,cacheImport,offline;
    String mode(){return doctor?"DOCTOR":envOnly?"ENV_ONLY":plan?"PLAN":cacheExport?"CACHE_EXPORT":cacheImport?"CACHE_IMPORT":infraOnly?"INFRA_ONLY":"START";}
    static Options parse(String[] args){
      Options result=new Options();List<String> tokens=Arrays.asList(args);
      for(int i=0;i<tokens.size();i++) {
        String value=tokens.get(i);
        switch(value){
          case "--help" -> result.help=true;
          case "--doctor","--check" -> result.doctor=true;
          case "--env-only" -> result.envOnly=true;
          case "--plan" -> result.plan=true;
          case "--start" -> result.start=true;
          case "--infra-only" -> result.infraOnly=true;
          case "--cache-export" -> result.cacheExport=true;
          case "--cache-import" -> result.cacheImport=true;
          case "--offline" -> result.offline=true;
          case "--json" -> result.json=true;
          case "--no-docker-desktop" -> result.startDockerDesktop=false;
          case "--skip-build" -> result.skipBuildDeprecated=true; // backward compatibility; no Java Docker build
          case "--smoke" -> throw new StartupException("--smoke unavailable until Phase 05 and never implicit");
          case "--repo" -> {
            if(i+1>=tokens.size() || tokens.get(i+1).startsWith("--"))
              throw new StartupException("--repo expects an existing directory path");
            result.repoPath=Path.of(tokens.get(++i));
          }
          default -> throw new StartupException("Unknown option " + value + "; run --help");
        }
      }
      int count=(result.doctor?1:0)+(result.envOnly?1:0)+(result.plan?1:0)+(result.start?1:0)
          +(result.infraOnly?1:0)+(result.cacheExport?1:0)+(result.cacheImport?1:0);
      if(count>1)throw new StartupException("Select exactly one of doctor/plan/env-only/infra-only/cache-export/cache-import/start");
      if(count==0 && !result.help)result.start=true;
      return result;
    }
  }
}
