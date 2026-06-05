#!/usr/bin/env python3
"""Find rules in the development YAML source tree."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from rule_source_io import DEFAULT_SOURCE_DIR, load_source_tree


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("query", help="Rule id, table, title/explanation text, field, or SQL fragment.")
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--limit", type=int, default=20)
    return parser.parse_args()


def searchable_text(rule: dict[str, Any]) -> str:
    parts: list[str] = []
    for field in ["id", "title", "explanation", "targetTable", "sql"]:
        value = rule.get(field)
        if isinstance(value, str):
            parts.append(value)
    for field in ["requiredFields"]:
        value = rule.get(field)
        if isinstance(value, list):
            parts.extend(str(item) for item in value)
    return "\n".join(parts)


def main() -> int:
    args = parse_args()
    _, _, rule_entries = load_source_tree(args.source_dir)
    query = args.query.casefold()
    matches = [
        entry
        for entry in rule_entries
        if query in searchable_text(entry.rule).casefold()
    ]
    matches.sort(key=lambda entry: str(entry.rule.get("id", "")))

    if not matches:
        print(f"No rule matched: {args.query}")
        return 1

    shown = matches[: args.limit]
    for index, entry in enumerate(shown):
        rule = entry.rule
        if index:
            print()
        print(str(rule.get("id", "<missing id>")))
        try:
            display_path = entry.path.relative_to(Path.cwd())
        except ValueError:
            display_path = entry.path
        print(f"file: {display_path.as_posix()}")
        print(f"enabled: {str(rule.get('enabled', '')).lower()}")
        print(f"targetTable: {rule.get('targetTable', '')}")
        print(f"title: {rule.get('title', '')}")
        print()
        print("sql:")
        print(str(rule.get("sql", "")).rstrip())

    if len(matches) > len(shown):
        print()
        print(f"... {len(matches) - len(shown)} more match(es). Use --limit to show more.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
