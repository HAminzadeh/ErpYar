#!/usr/bin/env python3
"""Read-only gateway health + anonymous-access boundary probe for development.

Run after Compose has started. No .env, user credentials or DB writes required.
This is not a full cross-company test; use smoke_test.py on test data for that.
"""
import argparse
import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def status(url, timeout):
    try:
        with urlopen(Request(url, method="GET"), timeout=timeout) as response:
            return response.status, response.read(100000)
    except HTTPError as err:
        return err.code, err.read(100000)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=float, default=10)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    checks = (
        ("gateway health", "/actuator/health", 200),
        ("identity anonymous denied", "/api/auth/me", 401),
        ("organization anonymous denied", "/api/holdings", 401),
    )
    ok = True
    for label, path, expected in checks:
        try:
            code, body = status(base + path, args.timeout)
            if code != expected:
                print(f"FAIL: {label}: expected HTTP {expected}, got {code}")
                ok = False
            elif path == "/actuator/health" and json.loads(body).get("status") != "UP":
                print("FAIL: gateway health was not UP")
                ok = False
            else:
                print(f"PASS: {label}: HTTP {code}")
        except (URLError, TimeoutError, ValueError, json.JSONDecodeError) as exc:
            print(f"FAIL: {label}: {type(exc).__name__} (check gateway availability)")
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
