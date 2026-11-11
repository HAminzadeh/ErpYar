# ErpYar Phase 02 — Docker-only infrastructure and portable PostgreSQL cache

## Supported architecture

**ERP Java services are host JVM processes and are NOT built/started in Docker by this phase.**
Only PostgreSQL 16 is deployed through `infra/compose.infrastructure.yml`. This file declares
`name: erp-phase1` and `postgres_data` so existing Docker volume
`erp-phase1_postgres_data` is reused from the original `compose.yaml` (no new blank database
is intentionally substituted). Existing gateway/other legacy containers are not deleted or
stopped automatically. Their old ports may need operator review before Phase 04 starts Java
processes. Do **not** use `docker compose down -v`.

## IntelliJ run and command-line operations

Use JDK 21 and reload Maven. Run `ir.erp.launcher.ErpYarLauncher` from module `erp-launcher`
with working directory `$PROJECT_DIR$` or use the shared `.run` configurations.
After building the launcher, these commands work from the repository root:

```powershell
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --doctor
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --plan
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --env-only
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --cache-export
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --cache-import --offline
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --infra-only --offline
# Allow a one-time registry pull if neither local Docker nor verified cache has the image:
java -cp tools/erp-launcher/target/classes ir.erp.launcher.ErpYarLauncher --infra-only
```

`--doctor` and `--plan` are read-only, and `--env-only` touches only `.env`.
`--infra-only` returns 0 only when PostgreSQL is reachable, authenticated and the six logical
DBs have been checked; it intentionally performs **no** Flyway migrations or JVM startup.
Default `--start` initializes infrastructure, then returns `DEFERRED_NOT_READY` (exit 3)
until customer-aware module planning and JVM supervision are completed in Phases 03/04.
`--cache-export` requires an actual Docker image present on this host, saves it as TAR and
records its SHA-256 and image ID in `.local/docker-images/manifest.json`. It will not pull.

## Offline image transfer

1. Run `--cache-export` on the source computer after its PostgreSQL image is present.
   Files are saved under `.local/docker-images/` **inside** the repository, intentionally ignored by Git.
2. Copy the **entire** `.local/docker-images` directory to the same relative path on the
   destination computer. For a portable ZIP, run
   `powershell -File scripts/Pack-ErpYar-Docker-Cache.ps1 -RepoPath "D:\ChatGPT_Projects\ErpYar"`.
   Extract that ZIP to a temporary folder and copy its `docker-images/` directory to `.local/`.
3. Install Docker Desktop on the destination computer with operator approval; configure
   Linux containers. JDK 21 and Maven/required artifacts must also be installed for JVM services.
4. Run `--cache-import --offline` and then `--infra-only --offline` from IntelliJ. No registry
   requests are made. `docker load` is only invoked after file size, SHA-256, schema, platform
   and archive path checks. Image ID must match the manifest afterward.

Windows `linux/amd64` and Linux `linux/arm64` Docker engines use distinct image archives:
`postgres-16-alpine-linux-amd64.tar` and `postgres-16-alpine-linux-arm64.tar`. Do not use a
cache created for another architecture without preparing the corresponding image and manifest.
Only infrastructure images are exported. Docker Desktop itself is **not** bundled in the cache.

Docker image tags are not immutable (`postgres:16-alpine` tracks patch updates). This
phase locks the **actual inspected image ID, repo digest when supplied, platform and archive hash**
inside the local manifest. A different image ID causes cached image restoration or a safe
error; an intentional image upgrade needs an explicit review and new cache/manifest version.
Do not remove or silently rotate the already-used Postgres volume.

Official Docker CLI reference:
- https://docs.docker.com/reference/cli/docker/image/save/
- https://docs.docker.com/reference/cli/docker/image/load/
- https://docs.docker.com/reference/compose-file/services/#pull_policy

## `.env`, password mismatch and six databases

The shared project `.env` is generated only if absent and completed only with missing keys:
`JWT_SECRET`, `DB_PASSWORD`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`,
`GATEWAY_PORT`, `ERP_DB_HOST_PORT`. Existing values stay unchanged. CLI uses `--env-file .env`;
Spring IDE profiles load `optional:file:./.env[.properties]` with working dir at repo root.
Actual process environment variables may override imported `.env` for Spring; therefore
remove conflicting IDE env overrides unless intentionally configured. `CommandRunner` removes
untrusted inherited Compose-interpolation variables before calling Docker.

PostgreSQL container binds only `127.0.0.1:${ERP_DB_HOST_PORT:-15432}`. Six service profiles
use their own database name in JDBC URLs and TCP loopback on the host. The initializer
checks `pg_isready`, compares a SHA-256 of the container's `POSTGRES_PASSWORD` environment
against `.env`, executes a **real password-authenticated psql TCP query inside the container**,
and validates the host TCP listener. This avoids confusing Windows' unrelated `5432`
instance with the ERP database. It does not send credentials to `psql` via CLI arguments.

`POSTGRES_PASSWORD` in Compose does **not** reset a role password already stored in a
persistent PostgreSQL volume. In that case error `28P01` is a deliberate safety stop.
Back up the volume and perform a separately approved, controlled password reconciliation.
Never delete a volume to make the error disappear. A host JDBC authentication check will
also run when the active Spring Boot service connects in Phase 04.

Existing DBs are not touched; missing `identity_db`, `organization_db`, `master_data_db`,
`workflow_db`, `notification_db`, `audit_db` are created by allow-listed SQL only. Existing
`infra/postgres/01-init-databases.sql` runs only on an **empty** Postgres data directory.
`DatabaseProvisioner` therefore checks each missing DB on subsequent launches too.

Flyway migration scripts `V1__init.sql` are checked for presence only. Flyway itself is
activated when the corresponding Java service runs (Phase 04); offline infrastructure-only
runs do not execute migrations on a user's real database. Do not edit applied migrations.

## Troubleshooting / known limits

- `OFFLINE: required image ... absent`: transfer the entire cache or permit an explicit
  online `--infra-only` run to pull the trusted ref once.
- `SHA-256 mismatch`: cache corrupt. Never load it. Re-export from a trusted host.
- `Unsupported ... platform`: Docker Desktop must run Linux containers; an amd64 image
  cannot be assumed compatible with a different CPU architecture.
- `28P01`: persistent credentials mismatch. Volume preserved.
- `docker compose ... config`: inspect `.env`, Compose v2 and permissions.
- `Cannot create ... database`: user `erp` may lack CREATEDB; manage permissions explicitly.
- Docker CLI/Maven/Java missing: manual/approved installation, not unreviewed silent installs.

Validation checklist: `mvn -B -ntp verify`; Python source contracts; `docker compose --env-file .env -f infra/compose.infrastructure.yml config --quiet`;
`--infra-only`; repeated `--infra-only --offline`; cache transfer to another compatible Docker
engine; terminal `--doctor`. Report skipped checks separately if runtime is unavailable.
