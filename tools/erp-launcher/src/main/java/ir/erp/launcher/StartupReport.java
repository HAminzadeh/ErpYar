package ir.erp.launcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** In-memory, machine-readable diagnostic. Never store credentials or raw command environment. */
public final class StartupReport {
  public enum Stage { PREFLIGHT, CONFIG, INFRA_PREP, SERVICE_PLAN, READY }
  public enum Level { ERROR, WARN, INFO }
  public enum Result { PASS, FAIL, WARNING, DEFERRED, NOT_RUN }

  public record Check(Stage stage, String id, Level level, Result result,
                      String message, String remedy, long durationMs) { }
  private final Instant at = Instant.now();
  private final String mode;
  private final List<Check> checks = new ArrayList<>();
  private String outcome = "INCOMPLETE";
  private int exitCode = -1;

  public StartupReport(String mode) { this.mode = mode; }
  public void add(Stage stage, String id, Level level, Result result,
                  String message, String remedy, long durationMs) {
    checks.add(new Check(stage, id, level, result, message, remedy, Math.max(0, durationMs)));
  }
  public boolean hasErrors() {
    return checks.stream().anyMatch(c -> c.level() == Level.ERROR && c.result() == Result.FAIL);
  }
  public List<Check> checks() { return List.copyOf(checks); }
  public String outcome() { return outcome; }
  public void outcome(String value) { this.outcome = value; }
  public void exitCode(int code) { this.exitCode = code; }

  public String toJson() {
    StringBuilder out = new StringBuilder(2048);
    out.append("{\"schemaVersion\":1,\"timestampUtc\":").append(q(at.toString()))
       .append(",\"mode\":").append(q(mode))
       .append(",\"outcome\":").append(q(outcome))
       .append(",\"exitCode\":").append(exitCode)
       .append(",\"checks\":[");
    for (int i = 0; i < checks.size(); i++) {
      Check c = checks.get(i);
      if (i > 0) out.append(',');
      out.append('{').append("\"stage\":").append(q(c.stage().name()))
         .append(",\"id\":").append(q(c.id()))
         .append(",\"level\":").append(q(c.level().name()))
         .append(",\"result\":").append(q(c.result().name()))
         .append(",\"message\":").append(q(c.message()))
         .append(",\"remedy\":").append(q(c.remedy()))
         .append(",\"durationMs\":").append(c.durationMs()).append('}');
    }
    return out.append("]}").toString();
  }

  public String toConsole() {
    StringBuilder result = new StringBuilder("ErpYar Phase 01 | " + mode + " | " + outcome + "\n");
    for (Check c : checks) {
      result.append(String.format(Locale.ROOT, "[%s] %-13s %-17s %s (%dms)%n",
          c.result(), c.stage(), c.id(), c.message(), c.durationMs()));
      if (!c.remedy().isBlank() && c.result() != Result.PASS) {
        result.append("       Action: ").append(c.remedy()).append('\n');
      }
    }
    return result.toString();
  }

  private static String q(String s) {
    StringBuilder out = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
      }
    }
    return out.append('"').toString();
  }
}
