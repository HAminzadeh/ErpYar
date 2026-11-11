package ir.erp.launcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static ir.erp.launcher.StartupReport.Level.*;
import static ir.erp.launcher.StartupReport.Result.*;
import static ir.erp.launcher.StartupReport.Stage.*;

/** Phase 02: initialize infrastructure without claiming selected JVM services are running. */
final class StartupOrchestrator {
  record Execution(StartupReport report,int exitCode){ Execution { report.exitCode(exitCode); } }
  private final Path root;
  private final ErpYarLauncher.Options options;
  private final ModulePlanProvider modulePlans=new Phase1ModulePlanProvider();
  private final JavaProcessSupervisor javaServices=new JavaServiceRuntime();
  StartupOrchestrator(Path root,ErpYarLauncher.Options options){this.root=root;this.options=options;}

  Execution execute(){
    StartupReport report=new StartupReport(options.mode());
    if(options.envOnly){
      long start=System.nanoTime();
      try {
        LocalEnvironment.ensure(root);
        report.add(CONFIG,"environment",INFO,PASS,"Local .env created/validated; existing secrets preserved","",ms(start));
        report.outcome("ENVIRONMENT_READY");return new Execution(report,0);
      } catch(StartupException ex){
        report.add(CONFIG,"environment",ERROR,FAIL,"Cannot prepare .env",ex.getMessage(),ms(start));
        report.outcome("FAILED");return new Execution(report,2);
      }
    }

    if(options.startDockerDesktop && !options.doctor && !options.plan)
      DockerToolDetector.tryStartExistingDesktop(new CommandRunner(root,Set.of()));
    new PrerequisiteChecker(root,report).inspect();
    if(options.doctor){report.outcome(report.hasErrors()?"DIAGNOSTIC_WITH_ERRORS":"DIAGNOSTIC_COMPLETE");
      return new Execution(report,report.hasErrors()?2:0);}
    if(options.plan){describePhases(report);
      report.outcome(report.hasErrors()?"PLAN_WITH_PREREQUISITE_ERRORS":"PLAN_ONLY_NOT_STARTED");
      return new Execution(report,report.hasErrors()?2:0);}
    if(report.hasErrors()){
      report.add(READY,"startup",ERROR,FAIL,"Required prerequisites not ready","Resolve ERROR checks with --doctor",0);
      report.outcome("FAILED");return new Execution(report,2);
    }

    long start=System.nanoTime();
    try {
      // Standalone cache operations need no application secrets or PostgreSQL runtime.
      if(options.cacheExport || options.cacheImport){
        DockerImageCacheManager images=new DockerImageCacheManager(root,new DockerProcessClient(root,Set.of()));
        if(options.cacheExport)images.exportImages();else images.importImages();
        report.add(INFRA_PREP,"image-cache",INFO,PASS,options.cacheExport
            ?"Existing Docker image archived and validated":"Verified local Docker image restored","",ms(start));
        report.outcome(options.cacheExport?"CACHE_EXPORTED":"CACHE_IMPORTED");
        return new Execution(report,0);
      }
      LocalEnvironment env=LocalEnvironment.ensure(root);
      report.add(CONFIG,"environment",INFO,PASS,"Local environment verified; secrets redacted","",ms(start));
      start=System.nanoTime();
      InfrastructureManager infrastructure=new InfrastructureManager(root,env,options.offline);
      infrastructure.startInfrastructure();
      report.add(INFRA_PREP,"postgresql",INFO,PASS,
          "PostgreSQL image/cache validated, Compose infrastructure started, credentials and databases checked","",ms(start));
      if(options.infraOnly){
        report.add(READY,"infrastructure-only",INFO,PASS,"Infrastructure ready; no Java ERP process was started","",0);
        report.outcome("INFRASTRUCTURE_READY");return new Execution(report,0);
      }
      describeJavaDeferred(report);
      report.add(READY,"startup",WARN,DEFERRED,
          "PostgreSQL ready, but selected Java services NOT RUNNING until Phase 04",
          "Use --infra-only for a successful infrastructure-only boot. Do not claim ERP is ready.",0);
      report.outcome("DEFERRED_NOT_READY");return new Execution(report,3);
    } catch(StartupException ex) {
      report.add(INFRA_PREP,"infrastructure",ERROR,FAIL,"Infrastructure initialization failed",ex.getMessage(),ms(start));
      report.outcome("FAILED");return new Execution(report,2);
    }
  }
  private void describePhases(StartupReport report) {
    report.add(INFRA_PREP,"infrastructure",INFO,PASS,
        "Phase 02 available: verified Docker cache, PostgreSQL-only Compose and six logical databases.","",0);
    describeJavaDeferred(report);
  }
  private void describeJavaDeferred(StartupReport report) {
    ModulePlanProvider.ModulePlan modulePlan=modulePlans.plan();
    report.add(SERVICE_PLAN,"module-plan",WARN,DEFERRED,modulePlan.reason(),
        "Phase 03: entitlement-aware module registry.",0);
    report.add(SERVICE_PLAN,"java-runtime",WARN,DEFERRED,javaServices.describe(),
        "Phase 04: launch selected host JVM services with health checks.",0);
  }
  private static long ms(long started){return Duration.ofNanos(System.nanoTime()-started).toMillis();}
}
