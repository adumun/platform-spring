#!/usr/bin/env python3

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import dependency_health as health  # noqa: E402


class DependencyHealthAnalyzerTest(unittest.TestCase):
    def test_dependency_tree_preserves_transitive_introduction_path(self):
        content = """\
[INFO] --- dependency:3.11.0:tree (default-cli) @ sample-app ---
[INFO] com.example:sample-app:jar:1.0
[INFO] +- org.example:direct:jar:2.0:compile
[INFO] |  \\- org.example:transitive:jar:3.0:compile
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "tree.txt"
            path.write_text(content, encoding="utf-8")
            paths = health.parse_dependency_paths(path)

        self.assertEqual(
            ["sample-app", "org.example:direct:2.0", "org.example:transitive:3.0"],
            paths["pkg:maven/org.example/transitive@3.0"],
        )

    def test_plugin_graph_preserves_plugin_to_dependency_path(self):
        content = """\
[INFO]    org.apache.maven.plugins:maven-compiler-plugin:maven-plugin:3.15.0:runtime
[INFO]       org.codehaus.plexus:plexus-utils:jar:4.0.2:runtime
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "plugins.txt"
            path.write_text(content, encoding="utf-8")
            components = {item["purl"]: item for item in health.parse_plugin_components(path)}

        self.assertEqual(
            [
                "org.apache.maven.plugins:maven-compiler-plugin:3.15.0",
                "org.codehaus.plexus:plexus-utils:4.0.2",
            ],
            components["pkg:maven/org.codehaus.plexus/plexus-utils@4.0.2"]["introduction_path"],
        )

    def test_local_policy_blocks_forbidden_coordinate_and_warns_missing_license(self):
        sbom = {
            "components": [
                {
                    "group": "org.example",
                    "name": "bad",
                    "version": "1.0",
                    "purl": "pkg:maven/org.example/bad@1.0",
                }
            ]
        }
        policy = {
            "blocked_coordinates": ["pkg:maven/org.example/bad@"],
            "license_policy": {"blocked_spdx": [], "missing_metadata": "WARN"},
        }
        result = health.analyze_local(sbom, policy)
        self.assertEqual("FAIL", result["status"])
        self.assertEqual(1, result["blocker_count"])
        self.assertEqual(1, result["warning_count"])

    def test_expired_or_incomplete_exception_is_invalid(self):
        policy = {
            "exception_contract": {
                "required_fields": [
                    "finding_type",
                    "finding_id",
                    "reason",
                    "owner",
                    "created_at",
                    "expires_at",
                    "compensating_controls",
                ]
            },
            "exceptions": [
                {
                    "finding_type": "vulnerability",
                    "finding_id": "OSV-TEST",
                    "reason": "fixture",
                    "owner": "platform",
                    "created_at": "2026-01-01",
                    "expires_at": "2026-01-02",
                    "compensating_controls": ["fixture"],
                },
                {"finding_type": "vulnerability", "finding_id": "MISSING-FIELDS"},
            ],
        }
        invalid = health.expired_exceptions(policy, health.dt.date(2026, 9, 7))
        self.assertEqual(2, len(invalid))


if __name__ == "__main__":
    unittest.main()
