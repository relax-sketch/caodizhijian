#!/usr/bin/env python3
"""Split the current embedded JSON rule set into development YAML sources."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any

from rule_source_io import (
    DEFAULT_OUTPUT_JSON,
    DEFAULT_SOURCE_DIR,
    dump_rule_yaml_file,
    dump_yaml_file,
    read_embedded_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_OUTPUT_JSON)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_SOURCE_DIR)
    parser.add_argument("--force", action="store_true", help="Overwrite an existing rules-src tree.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.source_dir.exists() and any(args.source_dir.iterdir()) and not args.force:
        print(f"Refusing to overwrite non-empty {args.source_dir}. Use --force to replace it.")
        return 1

    data = read_embedded_json(args.input)
    write_rule_set_files(args.source_dir, data)
    print(
        f"OK: split {len(data.get('rules', []))} rules and "
        f"{len(data.get('sources', []))} sources into {args.source_dir}."
    )
    return 0


def write_rule_set_files(source_dir: Path, data: dict[str, Any]) -> None:
    source_dir.mkdir(parents=True, exist_ok=True)
    dump_yaml_file(
        source_dir / "rule-set.yaml",
        {
            "schemaVersion": data.get("schemaVersion"),
            "ruleSetVersion": data.get("ruleSetVersion"),
            "publishedAt": data.get("publishedAt"),
        },
    )
    dump_yaml_file(source_dir / "sources/sources.yaml", {"sources": data.get("sources", [])})

    sources_by_id = {source.get("id"): source for source in data.get("sources", [])}
    used_names: set[str] = set()
    for rule in data.get("rules", []):
        rule.setdefault("enabled", True)
        source = sources_by_id.get(rule.get("sourceId"), {})
        group = "additional" if source.get("kind") == "ADDITIONAL" else "baseline"
        safe_id = safe_file_stem(str(rule.get("id", "rule")))
        file_name = unique_name(used_names, f"{safe_id}.yaml")
        dump_rule_yaml_file(source_dir / "rules" / group / file_name, rule)

    write_template(source_dir, data)


def write_template(source_dir: Path, data: dict[str, Any]) -> None:
    additional_source = next(
        (
            source
            for source in data.get("sources", [])
            if source.get("kind") == "ADDITIONAL"
        ),
        (data.get("sources") or [{}])[-1],
    )
    template = {
        "id": "ADD_GRASS_000",
        "sourceId": additional_source.get("id", ""),
        "enabled": True,
        "severity": "MANDATORY",
        "targetTable": "YD_TRCY_PT",
        "title": "Rule title",
        "explanation": "Rule explanation.",
        "requiredTables": ["YD_TRCY_PT"],
        "requiredFields": ["YD_ID"],
        "locatorFields": ["YD_ID"],
        "sql": "SELECT YD_ID FROM YD_TRCY_PT WHERE YD_ID = :ydId",
    }
    dump_rule_yaml_file(source_dir / "rules/additional/_template.yaml", template)


def safe_file_stem(value: str) -> str:
    stem = re.sub(r"[^A-Za-z0-9_.-]+", "_", value).strip("._")
    return stem or "rule"


def unique_name(used_names: set[str], name: str) -> str:
    if name not in used_names:
        used_names.add(name)
        return name
    stem = Path(name).stem
    suffix = Path(name).suffix
    index = 2
    while f"{stem}_{index}{suffix}" in used_names:
        index += 1
    result = f"{stem}_{index}{suffix}"
    used_names.add(result)
    return result


if __name__ == "__main__":
    raise SystemExit(main())
