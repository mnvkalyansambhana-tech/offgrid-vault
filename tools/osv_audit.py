#!/usr/bin/env python3
"""M9 dependency audit: every artifact shipped in the release APK, checked against OSV (osv.dev,
which aggregates GitHub Security Advisories, NVD-linked Maven advisories, etc.).

Run on a dev machine before each release:  python3 tools/osv_audit.py
Exit code 1 if any advisory is found. Read-only; sends only group:artifact:version to osv.dev.
"""
import json
import sys
import urllib.request

LOCKFILE = "app/gradle.lockfile"
CONFIGURATION = "releaseRuntimeClasspath"


def shipped():
    deps = set()
    for line in open(LOCKFILE):
        if "=" not in line or line.startswith("#"):
            continue
        coord, confs = line.strip().split("=", 1)
        if CONFIGURATION in confs.split(","):
            deps.add(coord)
    return sorted(deps)


def main():
    deps = shipped()
    found = {}
    for i in range(0, len(deps), 100):
        chunk = deps[i:i + 100]
        queries = [{"package": {"ecosystem": "Maven", "name": ":".join(d.split(":")[:2])}, "version": d.split(":")[2]} for d in chunk]
        request = urllib.request.Request(
            "https://api.osv.dev/v1/querybatch",
            data=json.dumps({"queries": queries}).encode(),
            headers={"Content-Type": "application/json"},
        )
        results = json.load(urllib.request.urlopen(request, timeout=60))["results"]
        for dep, result in zip(chunk, results):
            if result.get("vulns"):
                found[dep] = [v["id"] for v in result["vulns"]]
    print(f"{len(deps)} release runtime artifacts checked")
    for dep, ids in found.items():
        print(f"  {dep}: {', '.join(ids)}")
    print("no known advisories" if not found else f"{len(found)} artifact(s) with advisories")
    sys.exit(1 if found else 0)


if __name__ == "__main__":
    main()
