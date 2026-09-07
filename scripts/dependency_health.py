#!/usr/bin/env python3
"""Analyze platform-spring dependency health without third-party Python packages."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
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
MAVEN_COORDINATE = re.compile(
    r"(?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+):"
    r"(?P<type>[A-Za-z0-9_.-]+):(?P<version>[A-Za-z0-9_.+\-]+)(?::[A-Za-z0-9_.-]+)?"
)
TREE_COORDINATE = re.compile(
    r"(?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+):"
    r"(?P<type>[A-Za-z0-9_.-]+):(?:(?P<classifier>[A-Za-z0-9_.-]+):)?"
    r"(?P<version>[A-Za-z0-9_.+\-]+):(?P<scope>[A-Za-z0-9_.-]+)"
)


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
        if entry.get("expression"):
            values.append(str(entry["expression"]))
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


def maven_purl(group: str, artifact: str, version: str) -> str:
    return f"pkg:maven/{group}/{artifact}@{version}"


def normalize_info_line(raw: str) -> str:
    return raw[7:] if raw.startswith("[INFO] ") else raw


def parse_dependency_paths(path: Path | None) -> dict[str, list[str]]:
    """Return the shortest observed reactor introduction path per resolved Maven purl."""
    if path is None or not path.exists():
        return {}
    paths: dict[str, list[str]] = {}
    module = "reactor"
    stack: dict[int, str] = {}

    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = normalize_info_line(raw)
        if line.startswith("--- ") and " @ " in line:
            module = line.rsplit(" @ ", 1)[-1].rstrip(" -")
            stack.clear()
            continue
        marker_positions = [position for marker in ("+- ", "\\- ") if (position := line.find(marker)) >= 0]
        if not marker_positions:
            continue
        marker = min(marker_positions)
        candidate = line[marker + 3 :]
        match = TREE_COORDINATE.search(candidate)
        if not match:
            continue
        coordinate = f"{match.group('group')}:{match.group('artifact')}:{match.group('version')}"
        purl = maven_purl(match.group("group"), match.group("artifact"), match.group("version"))
        depth = marker // 3 + 1
        stack[depth] = coordinate
        for stale_depth in [key for key in stack if key > depth]:
            del stack[stale_depth]
        introduction = [module] + [stack[key] for key in sorted(stack) if key <= depth]
        existing = paths.get(purl)
        if existing is None or len(introduction) < len(existing):
            paths[purl] = introduction
    return paths


def parse_plugin_components(path: Path | None) -> list[dict[str, Any]]:
    """Parse Maven resolve-plugins output and preserve plugin -> dependency introduction paths."""
    if path is None or not path.exists():
        return []
    found: dict[str, dict[str, Any]] = {}
    active_plugin: str | None = None
    active_indent = 0

    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = normalize_info_line(raw)
        match = MAVEN_COORDINATE.search(line)
        if not match:
            continue
        group = match.group("group")
        artifact = match.group("artifact")
        version = match.group("version")
        if group == "io.adumun.platform":
            continue
        purl = maven_purl(group, artifact, version)
        coordinate = f"{group}:{artifact}:{version}"
        indent = len(line) - len(line.lstrip())
        is_plugin = match.group("type") in {"maven-plugin", "plugin"}

        if is_plugin or active_plugin is None or indent <= active_indent:
            active_plugin = coordinate
            active_indent = indent
            introduction = [coordinate]
        else:
            introduction = [active_plugin, coordinate]

        current = found.get(purl)
        candidate = {
            "purl": purl,
            "label": coordinate,
            "origin": "build-plugin-graph",
            "introduction_path": introduction,
        }
        if current is None or len(candidate["introduction_path"]) < len(current["introduction_path"]):
            found[purl] = candidate
    return sorted(found.values(), key=lambda item: item["purl"])


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
        if exception.get("finding_type") != finding_type or exception.get("finding_id") != finding_id:
            continue
        expected_component = exception.get("component")
        if expected_component and (not component_purl or not component_purl.startswith(str(expected_component))):
            continue
        if valid_exception(exception, today):
            return True, exception
    return False, None


def expired_exceptions(policy: dict[str, Any], today: dt.date) -> list[dict[str, Any]]:
    invalid: list[dict[str, Any]] = []
    required = set(policy.get("exception_contract", {}).get("required_fields", []) or [])
    for exception in policy.get("exceptions", []) or []:
        if required.difference(exception):
            invalid.append(exception)
            continue
        try:
            if dt.date.fromisoformat(str(exception["expires_at"])) < today:
                invalid.append(exception)
        except (ValueError, KeyError):
            invalid.append(exception)
    return invalid


def analyze_local(sbom: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    components = [c for c in sbom.get("components", []) or [] if isinstance(c, dict)]
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
        elif any(str(purl).startswith(str(prefix)) for prefix in blocked):
            blocked_components.append({"component": label, "purl": str(purl)})

        licenses = component_licenses(component)
        if not licenses:
            missing_license.append(label)
        denied = sorted(blocked_licenses.intersection(licenses))
        if denied:
            blocked_license_findings.append({"component": label, "purl": purl, "licenses": denied})

    blockers = len(blocked_components) + len(blocked_license_findings)
    warnings = len(missing_purl) + len(missing_license)
    return {
        "status": "FAIL" if blockers else ("WARN" if warnings else "PASS"),
        "component_count": len(components),
        "components_with_purl": len(components) - len(missing_purl),
        "missing_purl": missing_purl,
        "missing_license": missing_license,
        "blocked_components": blocked_components,
        "blocked_license_findings": blocked_license_findings,
        "blocker_count": blockers,
        "warning_count": warnings,
    }


def query_osv(
    sbom: dict[str, Any],
    dependency_paths: dict[str, list[str]],
    plugin_components: list[dict[str, Any]],
    policy: dict[str, Any],
    today: dt.date,
) -> dict[str, Any]:
    components_by_purl: dict[str, dict[str, Any]] = {}
    for component in sbom.get("components", []) or []:
        if not isinstance(component, dict) or not component.get("purl"):
            continue
        purl = str(component["purl"])
        if purl.startswith("pkg:maven/io.adumun.platform/"):
            continue
        components_by_purl[purl] = {
            "purl": purl,
            "label": component_label(component),
            "origin": "application-graph",
            "introduction_path": dependency_paths.get(purl, [component_label(component)]),
        }
    for plugin_component in plugin_components:
        components_by_purl.setdefault(plugin_component["purl"], plugin_component)

    components = sorted(components_by_purl.values(), key=lambda item: item["purl"])
    matches: list[dict[str, Any]] = []
    for batch in chunks(components, 500):
        response = http_json(OSV_BATCH_URL, payload={"queries": [{"package": {"purl": item["purl"]}} for item in batch]})
        results = response.get("results", []) or []
        if len(results) != len(batch):
            raise RuntimeError("OSV querybatch returned a result count different from the request")
        for component, result in zip(batch, results, strict=True):
            for vuln in result.get("vulns", []) or []:
                matches.append({
                    "component": component["label"],
                    "purl": component["purl"],
                    "origin": component["origin"],
                    "introduction_path": component["introduction_path"],
                    "id": vuln.get("id"),
                    "modified": vuln.get("modified"),
                })

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
        accepted += 1 if excepted else 0
        blockers += 0 if excepted else 1
        findings.append({
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
        })

    return {
        "source": "OSV",
        "application_component_count": sum(1 for c in components if c["origin"] == "application-graph"),
        "plugin_component_count": sum(1 for c in components if c["origin"] == "build-plugin-graph"),
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
        response = http_json(EOL_PRODUCT_URL.format(urllib.parse.quote(product, safe="")))
        result = response.get("result", response)
        release = next((candidate for candidate in result.get("releases", []) or []
                        if str(candidate.get("name")) == cycle or str(candidate.get("label")) == cycle), None)
        if release is None:
            raise RuntimeError(f"endoflife.date has no cycle '{cycle}' for product '{product}'")

        finding_id = f"{product}:{cycle}:EOL"
        excepted, exception = finding_excepted("lifecycle", finding_id, None, policy, today)
        unhealthy = bool(release.get("isEol")) or release.get("isMaintained") is False
        if unhealthy and not excepted:
            blockers += 1
        findings.append({
            "product": product,
            "cycle": cycle,
            "current_version": str(check.get("current_version", cycle)),
            "is_eol": bool(release.get("isEol")),
            "eol_from": release.get("eolFrom"),
            "is_maintained": release.get("isMaintained"),
            "latest": release.get("latest") or {},
            "state": "ACCEPTED_TEMPORARILY" if unhealthy and excepted else ("REMEDIATE" if unhealthy else "PASS"),
            "exception": exception,
            "source_generated_at": response.get("generated_at"),
        })

    return {"source": "endoflife.date", "finding_count": len(findings), "blocking_count": blockers, "findings": findings}


def run_local(args: argparse.Namespace) -> int:
    sbom = load_json(Path(args.sbom))
    policy = load_json(Path(args.policy))
    report_dir = Path(args.report_dir)
    local = analyze_local(sbom, policy)
    invalid_exceptions = expired_exceptions(policy, dt.date.today())
    blockers = local["blocker_count"] + len(invalid_exceptions)
    report = {
        "schema_version": 1,
        "mode": "local",
        "status": "FAIL" if blockers else local["status"],
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "local": local,
        "expired_or_invalid_exceptions": invalid_exceptions,
        "non_claims": [
            "local mode does not claim current vulnerability intelligence",
            "local mode does not claim current upstream lifecycle status",
            "availability of a newer version alone is not treated as a defect",
            "generic upstream bug trackers are not universally machine-detectable and are not claimed by this gate",
        ],
    }
    write_json(report_dir / "report.json", report)
    print(f"components inventoried: {local['component_count']}")
    if local["warning_count"]:
        print(f"local metadata warnings: {local['warning_count']}")
    return 1 if blockers else 0


def run_external(args: argparse.Namespace) -> int:
    sbom = load_json(Path(args.sbom))
    policy = load_json(Path(args.policy))
    report_dir = Path(args.report_dir)
    today = dt.date.today()
    local = analyze_local(sbom, policy)
    invalid_exceptions = expired_exceptions(policy, today)
    if local["blocker_count"] or invalid_exceptions:
        write_json(report_dir / "report.json", {
            "schema_version": 1, "mode": "full", "status": "FAIL",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "local": local, "expired_or_invalid_exceptions": invalid_exceptions,
            "external": {"status": "NOT_RUN_DUE_TO_LOCAL_BLOCKERS"},
        })
        print("local policy blockers prevent a full health claim")
        return 1

    dependency_paths = parse_dependency_paths(Path(args.dependency_tree) if args.dependency_tree else None)
    plugin_components = parse_plugin_components(Path(args.plugin_dependencies) if args.plugin_dependencies else None)
    try:
        osv = query_osv(sbom, dependency_paths, plugin_components, policy, today)
        lifecycle = query_lifecycle(policy, today)
    except RuntimeError as exc:
        write_json(report_dir / "report.json", {
            "schema_version": 1, "mode": "full", "status": "INCOMPLETE",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "local": local,
            "external": {"status": "SOURCE_UNAVAILABLE", "error": str(exc)},
            "non_claims": ["external intelligence was incomplete; dependency health is not claimed"],
        })
        print(str(exc))
        return 2

    write_json(report_dir / "osv-findings.json", osv)
    write_json(report_dir / "lifecycle-findings.json", lifecycle)
    blockers = osv["blocking_count"] + lifecycle["blocking_count"]
    status = "FAIL" if blockers else ("WARN" if local["warning_count"] else "PASS")
    write_json(report_dir / "report.json", {
        "schema_version": 1,
        "mode": "full",
        "status": status,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "local": local,
        "external": {"status": "FAIL" if blockers else "PASS", "osv": osv, "lifecycle": lifecycle},
        "expired_or_invalid_exceptions": invalid_exceptions,
        "policy": {
            "known_vulnerability_default": "REMEDIATE",
            "lifecycle_eol_default": "REMEDIATE",
            "upper_bound_drift": "ADVISORY_PENDING_BASELINE",
            "missing_license_metadata": policy.get("license_policy", {}).get("missing_metadata", "WARN"),
        },
        "non_claims": [
            "availability of a newer version alone is not treated as a defect",
            "generic upstream bug trackers are not universally machine-detectable and are not claimed by this gate",
        ],
    })
    print(f"OSV findings: {osv['finding_count']} ({osv['blocking_count']} blocking)")
    print(f"OSV components: {osv['application_component_count']} application + {osv['plugin_component_count']} plugin")
    print(f"Lifecycle checks: {lifecycle['finding_count']} ({lifecycle['blocking_count']} blocking)")
    print(f"SBOM components: {local['component_count']}")
    if local["warning_count"]:
        print(f"metadata warnings: {local['warning_count']}")
    return 1 if blockers else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("local", "external"):
        sub = subparsers.add_parser(command)
        sub.add_argument("--sbom", required=True)
        sub.add_argument("--policy", required=True)
        sub.add_argument("--report-dir", required=True)
        sub.add_argument("--dependency-tree")
        sub.add_argument("--plugin-dependencies")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    return run_local(args) if args.command == "local" else run_external(args)


if __name__ == "__main__":
    sys.exit(main())
