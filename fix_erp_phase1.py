#!/usr/bin/env python3
"""Minimal, safe repair for ERP Phase 1 Java authority generic type.

Place this file in the root of an existing ERP Phase 1 installation and run:
    py fix_erp_phase1.py

Only changes SecurityConfig.java. No .env/DB/container files are modified.
"""
from pathlib import Path
import sys

REL = Path('libs/erp-common/src/main/java/ir/erp/common/SecurityConfig.java')
OLD = b'java.util.List<SimpleGrantedAuthority> result=new java.util.ArrayList<>();'
NEW = b'java.util.List<org.springframework.security.core.GrantedAuthority> result=new java.util.ArrayList<>();'


def main() -> int:
    file = Path(__file__).resolve().parent / REL
    if not file.is_file():
        print('ERROR: SecurityConfig.java was not found:', file)
        print('Place this script in the ERP project root (next to pom.xml).')
        return 1
    original = file.read_bytes()
    if NEW in original and OLD not in original:
        print('Already fixed:', file)
        return 0
    if original.count(OLD) != 1 or NEW in original:
        print('ERROR: Unexpected file contents. No files changed:', file)
        return 1
    repaired = original.replace(OLD, NEW)
    file.write_bytes(repaired)
    print('FIXED:', file)
    print('Now run: docker compose up -d --build')
    return 0


if __name__ == '__main__':
    sys.exit(main())
