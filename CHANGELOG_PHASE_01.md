# ErpYar Smart Bootstrap Phase 01 — Implementation Summary

Base commit: `f2296dea4a3cf37253c3d536922d4b70e51af431` (GitHub main at the time of implementation).

- Correct explicit nested `--repo` and working-directory ancestor resolution; reject missing paths and symlinks.
- Replace obsolete all-Java-container launcher path with phase-aware startup orchestration.
- Introduce read-only Doctor / Plan, idempotent Env-only and honest Deferred Start modes with JSON reports.
- Detect Java 21, Maven 3.9+ (including IntelliJ bundled lookup), Docker CLI/Compose v2/Linux Engine, Windows WSL status, resources, writable workspace and reserved ports.
- Include manual, trusted tool installation recommendations; no silent OS installers or credential changes.
- Define typed expansion points for infrastructure-only Docker (Phase 02), module/customer planning (Phase 03) and independent Java process supervision (Phase 04).
- Add IntelliJ Run Configurations, JUnit regression tests, offline Java CLI integration tests, ADR and Persian operator guide.
- Preserve legacy `compose.yaml`, ide profiles and all business modules; do **not** run the legacy all-services Docker stack from Java launcher.

## Testing evidence

See ZIP reports/TEST_RESULTS.json. Actual Phase 01 execution: Java 21 compile and offline Python CLI tests in the artifact environment. Maven verify, IntelliJ Run on Windows and Docker runtime cannot be certified in that environment unless actually executed. No Commit/Push was performed.
