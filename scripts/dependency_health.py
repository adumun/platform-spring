#!/usr/bin/env python3
"""Dependency and supply-chain health analysis for platform-spring.

The Maven harness owns deterministic graph/effective-POM/enforcer/SBOM generation.
This companion analyzes the CycloneDX inventory and, in full mode, enriches it
with OSV vulnerability intelligence and explicit lifecycle checks.

No third-party Python packages are required.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable

USER_AGENT = "adumun-platform-spring-dependency-health/0.1"
OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULN_URL = "https://api.osv.dev/v1/vulns/{}"
EOL_PRODUCT_URL = "https://endoflife.date/api/v1/products/{}/"


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
        handle.write("\n")


def http_json(url: str, *, payload: Any | None = None, timeout: int = 30) -> Any:
    data = None
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"external source unavailable or invalid: {url}: {exc}") from exc


def chunks(items: list[Any], size: int) -> Iterable[list[Any]]:
    for index in range(0, len(items), size):
        yield items[index : index + size]


def component_licenses(component: dict[str, Any]) -> list[str]:
    values: list[str] = []
    for entry in component.get("licenses", []) or []:
        if not isinstance(entry, dict):
            continue
        expression = entry.get("expression")
        if expression:
            values.append(str(expression))
            continue
        license_entry = entry.get("license") or {}
        value = license_entry.get("id") or license_entry.get("name")
        if value:
            values.append(str(value))
    return sorted(set(values))


def component_label(component: dict[str, Any]) -> str:
    group = component.get("group") or ""
    name = component.get("name") or "unknown"
    version = component.get("version") or "unknown"
    return f"{group + ':' if group else ''}{name}:{version}"


def valid_exception(exception: dict[str, Any], today: dt.date) -> bool:
    expiry = exception.get("expires_at")
    if not expiry:
        return False
    try:
        return dt.date.fromisoformat(str(expiry)) >= today
    except ValueError:
        return False


def finding_excepted(
    finding_type: str,
    finding_id: str,
    component_purl: str | None,
    policy: dict[str, Any],
    today: dt.date,
) -> tuple[bool, dict[str, Any] | None]:
    for exception in policy.get("exceptions", []) or []:
        if exception.get("finding_type") != finding_type:
            continue
        if exception.get("finding_id") != finding_id:
            continue
        expected_component = exception.get("component")
        if expected_component and (not component_purl or not component_purl.startswith(str(expected_component))):
            continue
        if valid_exception(exception, today):
            return True, exception
    return False, None


def analyze_local(sbom: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    components = [component for component in sbom.get("components", []) or [] if isinstance(component, dict)]
    blocked = policy.get("blocked_coordinates", []) or []
    blocked_licenses = set(policy.get("license_policy", {}).get("blocked_spdx", []) or [])

    missing_purl: list[str] = []
    missing_license: list[str] = []
    blocked_components: list[dict[str, str]] = []
    blocked_license_findings: list[dict[str, Any]] = []

    for component in components:
        purl = component.get("purl")
        label = component_label(component)
        if not purl:
            missing_purl.append(label)
        if purl and any(str(purl).startswith(str(prefix)) for prefix in blocked):
            blocked_components.append({"component": label, "purl": str(purl)})

        licenses = component_licenses(component)
        if not licenses:
            missing_license.append(label)
        intersection = sorted(blocked_licenses.intersection(licenses))
        if intersection:
            blocked_license_findings.append(
                {"component": label, "purl": purl, "licenses": intersection}
            )

    blockers = len(blocked_components) + len(blocked_license_findings)
    warnings = len(missing_purl) + len(missing_license)
    status = "FAIL" if blockers else ("WARN" if warnings else "PASS")

    return {
        "status": status,
        "component_count": len(components),
        "components_with_purl": len(components) - len(missing_purl),
        "missing_purl": missing_purl,
        "missing_license": missing_license,
        "blocked_components": blocked_components,
        "blocked_license_findings": blocked_license_findings,
        "blocker_count": blockers,
        "warning_count": warnings,
    }


def query_osv(sbom: dict[str, Any], policy: dict[str, Any], today: dt.date) -> dict[str, Any]:
    components: list[dict[str, str]] = []
    seen_purls: set[str] = set()
    for component in sbom.get("components", []) or []:
        if not isinstance(component, dict):
            continue
        purl = component.get("purl")
        if not purl or str(purl) in seen_purls:
            continue
        if str(purl).startswith("pkg:maven/io.adumun.platform/"):
            continue
        seen_purls.add(str(purl))
        components.append({"purl": str(purl), "label": component_label(component)})

    matches: list[dict[str, Any]] = []
    for component_batch in chunks(components, 500):
        response = http_json(
            OSV_BATCH_URL,
            payload={"queries": [{"package": {"purl": item["purl"]}} for item in component_batch]},
        )
        results = response.get("results", []) or []
        if len(results) != len(component_batch):
            raise RuntimeError("OSV querybatch returned a result count different from the request")
        for component, result in zip(component_batch, results, strict=True):
            for vuln in result.get("vulns", []) or []:
                matches.append(
                    {
                        "component": component["label"],
                        "purl": component["purl"],
                        "id": vuln.get("id"),
                        "modified": vuln.get("modified"),
                    }
                )

    detail_cache: dict[str, Any] = {}
    findings: list[dict[str, Any]] = []
    blockers = 0
    accepted = 0
    for match in matches:
        vuln_id = match.get("id")
        if not vuln_id:
            continue
        if vuln_id not in detail_cache:
            detail_cache[vuln_id] = http_json(OSV_VULN_URL.format(urllib.parse.quote(str(vuln_id), safe="")))
        detail = detail_cache[vuln_id]
        excepted, exception = finding_excepted("vulnerability", str(vuln_id), match.get("purl"), policy, today)
        state = "ACCEPTED_TEMPORARILY" if excepted else "REMEDIATE"
        if excepted:
            accepted += 1
        else:
            blockers += 1
        findings.append(
            {
                **match,
                "summary": detail.get("summary"),
                "details": detail.get("details"),
                "aliases": detail.get("aliases", []),
                "severity": detail.get("severity", []),
                "database_specific": detail.get("database_specific", {}),
                "published": detail.get("published"),
                "modified": detail.get("modified", match.get("modified")),
                "state": state,
                "exception": exception,
            }
        )

    return {
        "source": "OSV",
        "queried_component_count": len(components),
        "finding_count": len(findings),
        "blocking_count": blockers,
        "accepted_temporary_count": accepted,
        "findings": findings,
    }


def query_lifecycle(policy: dict[str, Any], today: dt.date) -> dict[str, Any]:
    findings: list[dict[str, Any]] = []
    blockers = 0
    for check in policy.get("lifecycle_checks", []) or []:
        product = str(check["product"])
        cycle = str(check["cycle"])
        current_version = str(check.get("current_version", cycle))
        response = http_json(EOL_PRODUCT_URL.format(urllib.parse.quote(product, safe="")))
        result = response.get("result", response)
        releases = result.get("releases", []) or []
        release = next(
            (
                candidate
                for candidate in releases
                if str(candidate.get("name")) == cycle or str(candidate.get("label")) == cycle
            ),
            None,
        )
        if release is None:
            raise RuntimeError(f"endoflife.date has no cycle '{cycle}' for product '{product}'")

        is_eol = bool(release.get("isEol"))
        is_maintained = release.get("isMaintained")
        finding_id = f"{product}:{cycle}:EOL"
        excepted, exception = finding_excepted("lifecycle", finding_id, None, policy, today)
        state = "PASS"
        if is_eol or is_maintained is False:
            if excepted:
                state = "ACCEPTED_TEMPORARILY"
            else:
                state = "REMEDIATE"
                blockers += 1

        latest = release.get("latest") or {}
        findings.append(
            {
                "product": product,
                "cycle": cycle,
                "current_version": current_version,
                "is_eol": is_eol,
                "eol_from": release.get("eolFrom"),
                "is_maintained": is_maintained,
                "latest": latest,
                "state": state,
                "exception": exception,
                "source_generated_at": response.get("generated_at"),
            }
        )

    return {
        "source": "endoflife.date",
        "finding_count": len(findings),
        "blocking_count": blockers,
        "findings": findings,
    }


def expired_exceptions(policy: dict[str, Any], today: dt.date) -> list[dict[str, Any]]:
    expired: list[dict[str, Any]] = []
    for exception in policy.get("exceptions", []) or []:
        expiry = exception.get("expires_at")
        if not expiry:
            expired.append(exception)
            continue
        try:
            if dt.date.fromisoformat(str(expiry)) < today:
                expired.append(exception)
        except ValueError:
            expired.append(exception)
    return expired


def run_local(args: argparse.Namespace) -> int:
    sbom = load_json(Path(args.sbom))
    policy = load_json(Path(args.policy))
    report_dir = Path(args.report_dir)
    local = analyze_local(sbom, policy)
    expired = expired_exceptions(policy, dt.date.today())
    blockers = local["blocker_count"] + len(expired)
    status = "FAIL" if blockers else local["status"]
    report = {
        "schema_version": 1,
        "mode": "local",
        "status": status,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "local": local,
        "expired_or_invalid_exceptions": expired,
        "non_claims": [
            "local mode does not claim current vulnerability intelligence",
            "local mode does not claim current upstream lifecycle status",
        ],
    }
    write_json(report_dir / "report.json", report)
    if blockers:
        print(f"blocking local findings: {blockers}")
        return 1
    if local["warning_count"]:
        print(f"local warnings: {local['warning_count']}")
    print(f"components inventoried: {local['component_count']}")
    return 0


def run_external(args: argparse.Namespace) -> int:
    sbom = load_json(Path(args.sbom))
    policy = load_json(Path(args.policy))
    report_dir = Path(args.report_dir)
    today = dt.date.today()

    local = analyze_local(sbom, policy)
    expired = expired_exceptions(policy, today)
    if local["blocker_count"] or expired:
        report = {
            "schema_version": 1,
            "mode": "full",
            "status": "FAIL",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "local": local,
            "expired_or_invalid_exceptions": expired,
            "external": {"status": "NOT_RUN_DUE_TO_LOCAL_BLOCKERS"},
        }
        write_json(report_dir / "report.json", report)
        print("local policy blockers prevent a full health claim")
        return 1

    try:
        osv = query_osv(sbom, policy, today)
        lifecycle = query_lifecycle(policy, today)
    except RuntimeError as exc:
        report = {
            "schema_version": 1,
            "mode": "full",
            "status": "INCOMPLETE",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "local": local,
            "external": {"status": "SOURCE_UNAVAILABLE", "error": str(exc)},
            "non_claims": ["external intelligence was incomplete; dependency health is not claimed"],
        }
        write_json(report_dir / "report.json", report)
        print(str(exc))
        return 2

    write_json(report_dir / "osv-findings.json", osv)
    write_json(report_dir / "lifecycle-findings.json", lifecycle)

    blockers = osv["blocking_count"] + lifecycle["blocking_count"]
    status = "FAIL" if blockers else ("WARN" if local["warning_count"] else "PASS")
    report = {
        "schema_version": 1,
        "mode": "full",
        "status": status,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "local": local,
        "external": {
            "status": "FAIL" if blockers else "PASS",
            "osv": osv,
            "lifecycle": lifecycle,
        },
        "expired_or_invalid_exceptions": expired,
        "policy": {
            "known_vulnerability_default": "REMEDIATE",
            "lifecycle_eol_default": "REMEDIATE",
            "upper_bound_drift": "ADVISORY_PENDING_BASELINE",
            "missing_license_metadata": policy.get("license_policy", {}).get("missing_metadata", "WARN"),
        },
    }
    write_json(report_dir / "report.json", report)

    print(f"OSV findings: {osv['finding_count']} ({osv['blocking_count']} blocking)")
    print(f"Lifecycle checks: {lifecycle['finding_count']} ({lifecycle['blocking_count']} blocking)")
    print(f"SBOM components: {local['component_count']}")
    if local["warning_count"]:
        print(f"Metadata warnings: {local['warning_count']}")
    return 1 if blockers else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("local", "external"):
        sub = subparsers.add_parser(command)
        sub.add_argument("--sbom", required=True)
        sub.add_argument("--policy", required=True)
        sub.add_argument("--report-dir", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.command == "local":
        return run_local(args)
    return run_external(args)


if __name__ == "__main__":
    sys.exit(main())
