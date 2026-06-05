#!/usr/bin/env python3
"""Build the Android embedded rule-set JSON from development YAML sources."""

from __future__ import annotations

import argparse
import shutil
from datetime import datetime
from pathlib import Path

from rule_source_io import (
    DEFAULT_OUTPUT_JSON,
    DEFAULT_SOURCE_DIR,
    build_embedded_rule_set,
    load_source_tree,
    validate_source_tree,
    write_embedded_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT_JSON)
    parser.add_argument(
        "--backup",
        action="store_true",
        help="Copy the existing output to rule-set.json.backup-YYYYMMDD-HHMMSS first.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root, sources, rule_entries = load_source_tree(args.source_dir)
    errors, warnings = validate_source_tree(root, sources, rule_entries)

    for warning in warnings:
        print(warning.format(args.source_dir))
        print()
    if errors:
        for error in errors:
            print(error.format(args.source_dir))
            print()
        print(f"FAILED: {len(errors)} error(s), {len(warnings)} warning(s).")
        return 1

    rule_set = build_embedded_rule_set(root, sources, rule_entries)
    if args.backup and args.output.is_file():
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        backup = args.output.with_name(f"{args.output.name}.backup-{stamp}")
        shutil.copy2(args.output, backup)
        print(f"Backup: {backup}")
    write_embedded_json(args.output, rule_set)
    enabled_count = sum(1 for rule in rule_set["rules"] if rule.get("enabled") is True)
    disabled_count = len(rule_set["rules"]) - enabled_count
    print(
        f"OK: wrote {len(rule_set['rules'])} rules from "
        f"{len(rule_set['sources'])} sources to {args.output} "
        f"({enabled_count} enabled, {disabled_count} disabled)."
    )
    if warnings:
        print(f"Warnings: {len(warnings)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
