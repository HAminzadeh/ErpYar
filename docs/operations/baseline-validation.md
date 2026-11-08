# Phase 00 — local execution and troubleshooting

## Safety rules

- Work on `HAminzadeh/ErpYar` at **exact** commit `1c0ba491ec02f1bd380028b50dd232398a64f197` with a **clean Git working tree**, including no untracked files (ignored local `.env` is permitted).
- Download the delivered ZIP and `Apply-ErpYar-Phase.ps1` into your Downloads folder. Keep your own backups of databases and important environment files.
- The installer validates manifest fields, ZIP entries and SHA-256 hashes before it writes files, and backs up files it overwrites. It never performs commit, push, reset, database modification or container removal.
- The installer only applies paths under the repository root, never `.git` or `.env`. If the local HEAD has advanced, it deliberately aborts; rebase/review the patch separately instead of forcing it.

## PowerShell example

```powershell
& "$HOME\Downloads\Apply-ErpYar-Phase.ps1" -Phase 0 -RepoPath "C:\path\to\ErpYar"
```

To run optional read-only/check/build commands after application, add `-RunTests`. The smoke test is intentionally **not** run by the installer because it creates database records.

## Verify locally after apply

```powershell
py scripts/verify_baseline.py
py -m unittest discover -s scripts/tests -p 'test_*.py' -v
mvn -B -ntp verify
# Compose interpolation requires local .env; create one only if none exists:
py scripts/init_env.py
docker compose config --quiet
docker compose up -d --build
Invoke-RestMethod http://localhost:8080/actuator/health
py scripts/phase00_gateway_check.py
# Run only on a dev/test database; this script inserts demo records:
py scripts/smoke_test.py
# Inspect changes before committing and pushing yourself:
git status --short
git diff --check
git diff --stat
```

If `mvn` is unavailable locally, use a Java 21 + Maven 3.9 environment or the existing README's containerized Maven command. Start Docker Desktop before using Compose. The static checker is safe without Java, Maven, Docker or database access; it checks structure only and prints exact `NOT_RUN` runtime statuses.

## Failure / backup / recovery

- **SHA mismatch**: the ZIP was modified, truncated or did not correspond to its manifest. Download the original artifact; do not bypass verification.
- **Git SHA mismatch**: checkout is not the reviewed baseline. Do not reset data or force the patch onto an unknown version; compare branches and use a new verified package.
- **Dirty worktree**: stash/commit your own changes deliberately elsewhere before retrying; the installer won't overwrite them.
- **PowerShell ZIP missing**: specify the exact downloaded ZIP with `-ZipPath`. Auto-discovery searches Downloads and the script's own directory, preferring the newest matching phase file.
- **Installer copy failure**: it attempts to restore overwritten files and removes only the files created by this attempt. The backup folder path is printed and preserved for manual recovery if the process was interrupted by power loss.
- **Maven/Compose failure**: preserve the log and report the exact failing module/step. Do not label the phase green merely because the static checks pass.
- **Health or cross-company smoke failure**: examine `docker compose ps` and `docker compose logs --tail=100`, service JWT/DB configuration, then investigate without clearing volumes. Never run a volume-destructive command to fix a baseline check.

## Evidence checklist

Record the local command, environment, date, exit code and redacted log of each: Python audit, Python tests, Maven verify, Compose config, health GET, smoke test. Do not share `.env`, JWTs, passwords or real customer records. A clean bootstrap should be evaluated in a separate disposable development environment and is not automatically initiated by the installer.
