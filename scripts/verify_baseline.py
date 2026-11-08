#!/usr/bin/env python3
"""Dependency-free, read-only ErpYar Phase 00 repository baseline inventory.

Requires Python 3.9+. No network, application credentials, or database access.
Reports structural evidence, not a substitute for Maven, Compose or smoke tests.
"""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

MODULES = (
    "libs/erp-common", "services/gateway", "services/identity",
    "services/organization", "services/master-data", "services/workflow",
    "services/notification", "services/audit",
)
BUSINESS = ("identity", "organization", "master-data", "workflow", "notification", "audit")
DATABASES = {
    "identity": "identity_db", "organization": "organization_db",
    "master-data": "master_data_db", "workflow": "workflow_db",
    "notification": "notification_db", "audit": "audit_db",
}
SENSITIVE_BASENAMES = {".env", "id_rsa", "id_ed25519", "credentials.json", "secrets.yml", "secrets.yaml"}
SENSITIVE_SUFFIXES = (".p12", ".pfx", ".pem", ".key", ".jks", ".keystore")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def git(root: Path, *args: str) -> str | None:
    try:
        return subprocess.check_output(["git", "-C", str(root), *args], text=True, stderr=subprocess.DEVNULL).strip()
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None


def inspect(root: Path) -> dict:
    root = root.resolve()
    errors: list[str] = []
    warnings: list[str] = []
    pom = root / "pom.xml"
    modules: list[str] = []
    java = boot = cloud = None
    if not pom.is_file():
        errors.append("Missing root pom.xml")
    else:
        try:
            tree = ET.parse(pom)
            modules = [x.text.strip() for x in tree.findall("m:modules/m:module", NS) if x.text]
            java = tree.findtext("m:properties/m:java.version", namespaces=NS)
            cloud = tree.findtext("m:properties/m:spring-cloud.version", namespaces=NS)
            boot = tree.findtext("m:parent/m:version", namespaces=NS)
        except (ET.ParseError, OSError) as exc:
            errors.append(f"pom.xml cannot be parsed: {type(exc).__name__}")
    for m in MODULES:
        if m not in modules:
            errors.append(f"Required Maven module not declared: {m}")
    for m in modules:
        if not (root / m / "pom.xml").is_file():
            errors.append(f"Maven module lacks pom.xml: {m}")
    if java != "21":
        errors.append(f"Java baseline must be 21; found {java!r}")
    if not boot or not cloud:
        errors.append("Spring Boot / Spring Cloud versions not located in root pom")
    compose = root / "compose.yaml"
    compose_text = compose.read_text(encoding="utf-8") if compose.is_file() else ""
    if not compose_text:
        errors.append("Missing compose.yaml")
    database_init = root / "infra/postgres/01-init-databases.sql"
    db_sql = database_init.read_text(encoding="utf-8") if database_init.is_file() else ""
    if not db_sql:
        errors.append("Missing DB initialization SQL")
    gateway = root / "services/gateway/src/main/resources/application.yml"
    routes = gateway.read_text(encoding="utf-8") if gateway.is_file() else ""
    if not routes:
        errors.append("Missing gateway application.yml")
    migrations: dict[str, list[str]] = {}
    for service in BUSINESS:
        migration_dir = root / f"services/{service}/src/main/resources/db/migration"
        migrations[service] = sorted(p.name for p in migration_dir.glob("V*__*.sql")) if migration_dir.is_dir() else []
        if not migrations[service]:
            errors.append(f"No Flyway SQL migration found for {service}")
        if DATABASES[service] not in db_sql:
            errors.append(f"DB init lacks {DATABASES[service]}")
        if f"DB_NAME: {DATABASES[service]}" not in compose_text:
            errors.append(f"Compose lacks DB_NAME for {service}")
        if f"http://{service}:8080" not in routes and service != "master-data":
            # master-data's gateway environment key is MASTER_URL; URI still includes service name.
            errors.append(f"Gateway route missing backend for {service}")
        if service == "master-data" and "http://master-data:8080" not in routes:
            errors.append("Gateway route missing backend for master-data")
    if "/actuator/health" not in routes and "include: health" not in routes:
        warnings.append("Gateway health exposure was not found by static inspection")
    for item in ("scripts/init_env.py", "scripts/smoke_test.py", "Dockerfile"):
        if not (root / item).is_file():
            errors.append(f"Missing bootstrap element: {item}")
    tracked_raw = git(root, "ls-files", "-z")
    sensitive_paths = []
    if tracked_raw is not None:
        # Git emits NUL-delimited paths even when filenames contain whitespace.
        # text=True preserves the NUL bytes.
        for item in tracked_raw.split("\x00"):
            if not item:
                continue
            lower = Path(item).name.lower()
            if lower in SENSITIVE_BASENAMES or lower.endswith(SENSITIVE_SUFFIXES):
                sensitive_paths.append(item)
        if sensitive_paths:
            errors.append("Tracked sensitive filename(s): " + ", ".join(sensitive_paths))
    else:
        warnings.append("Git unavailable or directory is not a Git worktree; tracked secret-file inspection skipped")
    if (root / "erp-phase1-source.zip").is_file():
        warnings.append("Legacy binary erp-phase1-source.zip is tracked; nested contents not verified by this audit")
    if "${JWT_SECRET:?" not in compose_text or "${DB_PASSWORD:?" not in compose_text:
        warnings.append("Compose required-variable guards could not be confirmed")
    return {
        "phase": 0,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "repository": "HAminzadeh/ErpYar",
        "gitHead": git(root, "rev-parse", "HEAD"),
        "gitBranch": git(root, "branch", "--show-current"),
        "maven": {"modules": modules, "java": java, "springBoot": boot, "springCloud": cloud},
        "services": {s: {"database": DATABASES[s], "migrations": migrations[s]} for s in BUSINESS},
        "gateway": {"module": "services/gateway", "healthConfigurationPresent": bool(routes and "health" in routes)},
        "checks": {"structural": "PASS" if not errors else "FAIL", "mavenVerify": "NOT_RUN", "dockerComposeConfig": "NOT_RUN", "smokeTest": "NOT_RUN"},
        "errors": errors,
        "warnings": warnings,
    }


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1], help="repository root")
    p.add_argument("--json-out", type=Path, default=None, help="optional path for JSON report")
    args = p.parse_args(argv)
    result = inspect(args.repo)
    output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    print(output, end="")
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(output, encoding="utf-8")
    return 0 if not result["errors"] else 1


if __name__ == "__main__":
    sys.exit(main())
