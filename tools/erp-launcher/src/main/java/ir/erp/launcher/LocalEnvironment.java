package ir.erp.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Creates only missing local configuration keys; never changes existing secret values. */
public final class LocalEnvironment {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final List<String> REQUIRED = List.of(
      "JWT_SECRET", "DB_PASSWORD", "BOOTSTRAP_ADMIN_USERNAME", "BOOTSTRAP_ADMIN_PASSWORD",
      "GATEWAY_PORT", "ERP_DB_HOST_PORT");
  private final Map<String, String> values;

  private LocalEnvironment(Map<String, String> values) { this.values = Map.copyOf(values); }

  public static LocalEnvironment checkWithoutWriting(Path root) {
    Path env = root.resolve(".env");
    if (!Files.isRegularFile(env)) {
      throw new StartupException(".env missing. Run the launcher normally to create it without overwriting data.");
    }
    return new LocalEnvironment(validate(parse(env)));
  }

  public static LocalEnvironment ensure(Path root) {
    Path env = root.resolve(".env");
    if (Files.isSymbolicLink(env)) {
      throw new StartupException("Refusing to write .env through a symbolic link: " + env);
    }
    try {
      if (!Files.exists(env)) {
        String content = "# ErpYar local development only; DO NOT COMMIT\n"
            + "JWT_SECRET=" + random(48) + "\n"
            + "DB_PASSWORD=" + random(32) + "\n"
            + "BOOTSTRAP_ADMIN_USERNAME=admin\n"
            + "BOOTSTRAP_ADMIN_PASSWORD=" + random(32) + "\n"
            + "GATEWAY_PORT=8080\n"
            + "ERP_DB_HOST_PORT=15432\n";
        try {
          Files.writeString(env, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
          tryRestrictPermissions(env);
          System.err.println("[ErpYar] Created .env using cryptographically random credentials.");
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
          // Another initializer created it concurrently; inspect without rewriting existing values.
        }
      }
      Map<String, String> existing = parse(env);
      List<String> added = new ArrayList<>();
      for (String key : REQUIRED) {
        if (!existing.containsKey(key)) {
          String value = defaultValue(key);
          added.add(key + "=" + value);
          existing.put(key, value);
        }
      }
      // For existing files, append only genuinely absent entries and preserve every existing line.
      if (!added.isEmpty()) {
        byte[] raw = Files.readAllBytes(env);
        String prefix = raw.length > 0 && raw[raw.length - 1] != '\n' ? "\n" : "";
        Files.writeString(env, prefix + String.join("\n", added) + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.APPEND);
        System.err.println("[ErpYar] Added " + added.size() + " missing .env keys without changing existing values.");
      }
      return new LocalEnvironment(validate(parse(env)));
    } catch (IOException e) {
      throw new StartupException("Cannot prepare local .env. Check directory permissions: " + e.getClass().getSimpleName(), e);
    }
  }

  private static String random(int bytes) {
    byte[] value = new byte[bytes];
    RANDOM.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static String defaultValue(String key) {
    return switch (key) {
      case "JWT_SECRET" -> random(48);
      case "DB_PASSWORD", "BOOTSTRAP_ADMIN_PASSWORD" -> random(32);
      case "BOOTSTRAP_ADMIN_USERNAME" -> "admin";
      case "GATEWAY_PORT" -> "8080";
      case "ERP_DB_HOST_PORT" -> "15432";
      default -> throw new IllegalArgumentException("Unknown .env field");
    };
  }

  static Map<String, String> parse(Path env) {
    if (Files.isSymbolicLink(env)) {
      throw new StartupException("Refusing symbolic-link .env configuration.");
    }
    try {
      Map<String, String> output = new HashMap<>();
      Set<String> duplicates = new HashSet<>();
      for (String raw : Files.readAllLines(env, StandardCharsets.UTF_8)) {
        String line = raw.trim();
        if (line.isEmpty() || line.startsWith("#")) continue;
        int equals = line.indexOf('=');
        if (equals <= 0) {
          throw new StartupException("Invalid .env entry; use KEY=value (values are not printed).");
        }
        String key = line.substring(0, equals).trim();
        String value = line.substring(equals + 1).trim();
        if (!key.matches("[A-Z][A-Z0-9_]*")) {
          throw new StartupException("Invalid .env key name: " + key);
        }
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
          value = value.substring(1, value.length() - 1);
        }
        if (output.putIfAbsent(key, value) != null) duplicates.add(key);
      }
      if (!duplicates.isEmpty()) {
        throw new StartupException("Duplicate .env keys: " + String.join(", ", duplicates)
            + ". Resolve manually; the launcher does not guess which value is authoritative.");
      }
      return output;
    } catch (IOException e) {
      throw new StartupException("Cannot read .env: " + e.getClass().getSimpleName(), e);
    }
  }

  private static Map<String, String> validate(Map<String, String> values) {
    for (String key : REQUIRED) {
      if (!values.containsKey(key) || values.get(key).isBlank()) {
        throw new StartupException("Required local .env key missing or blank: " + key);
      }
    }
    if (values.get("JWT_SECRET").getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new StartupException("JWT_SECRET must be at least 32 UTF-8 bytes. Existing secret was not overwritten.");
    }
    if (values.get("BOOTSTRAP_ADMIN_PASSWORD").length() < 16) {
      throw new StartupException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 16 characters.");
    }
    validatePort(values.get("GATEWAY_PORT"), "GATEWAY_PORT");
    validatePort(values.get("ERP_DB_HOST_PORT"), "ERP_DB_HOST_PORT");
    return values;
  }

  private static void validatePort(String number, String key) {
    try {
      int port = Integer.parseInt(number);
      if (port < 1 || port > 65535) throw new NumberFormatException();
    } catch (NumberFormatException e) {
      throw new StartupException("Invalid " + key + " in .env (expected TCP port 1..65535)");
    }
  }

  private static void tryRestrictPermissions(Path path) {
    try {
      Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException | IOException ignored) {
      // Windows NTFS ACLs apply; .gitignore still prevents accidental staging.
    }
  }

  public String get(String key) { return values.get(key); }
  public int gatewayPort() { return Integer.parseInt(values.get("GATEWAY_PORT")); }
  public Set<String> secrets() {
    return new HashSet<>(List.of(values.get("JWT_SECRET"), values.get("DB_PASSWORD"),
        values.get("BOOTSTRAP_ADMIN_PASSWORD")));
  }
}
