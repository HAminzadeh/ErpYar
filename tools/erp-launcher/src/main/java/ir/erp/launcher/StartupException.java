package ir.erp.launcher;

/** Human-readable actionable startup failure; no credentials should enter the message. */
public final class StartupException extends RuntimeException {
  public StartupException(String message) { super(message); }
  public StartupException(String message, Throwable cause) { super(message, cause); }
}
