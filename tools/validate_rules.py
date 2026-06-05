#!/usr/bin/env python3
"""Validate development YAML rule sources before generating Android assets."""

from __future__ import annotations

import argparse
from pathlib import Path

from rule_source_io import DEFAULT_SOURCE_DIR, load_source_tree, validate_source_tree


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root, sources, rule_entries = load_source_tree(args.source_dir)
    errors, warnings = validate_source_tree(root, sources, rule_entries)

    for warning in warnings:
        print(warning.format(args.source_dir))
        print()
    for error in errors:
        print(error.format(args.source_dir))
        print()

    if errors:
        print(
            f"FAILED: {len(rule_entries)} rules checked, "
            f"{len(errors)} error(s), {len(warnings)} warning(s)."
        )
        return 1
    enabled_count = sum(1 for entry in rule_entries if entry.rule.get("enabled") is True)
    disabled_count = len(rule_entries) - enabled_count
    print(
        f"OK: {len(rule_entries)} rules validated "
        f"({enabled_count} enabled, {disabled_count} disabled)."
    )
    if warnings:
        print(f"Warnings: {len(warnings)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
