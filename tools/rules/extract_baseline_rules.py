#!/usr/bin/env python3
"""Extract enabled built-in ZDB quality rules into the Android embedded format."""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
from pathlib import Path
from typing import Any


DEFAULT_SOURCE_ID = "zdb-baseline-20260526"
REFERENCE_TABLE_PATTERN = re.compile(r"\b(?:FROM|JOIN)\s+([A-Za-z_][A-Za-z0-9_]*)", re.I)


def read_enabled_rules(database_path: Path) -> tuple[list[dict[str, Any]], set[str]]:
    connection = sqlite3.connect(database_path.resolve().as_uri() + "?mode=ro", uri=True)
    connection.execute("PRAGMA query_only=ON")
    try:
        source_tables = {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            )
        }
        rows = connection.execute(
            """
            SELECT objectid, c_datachectitemname, b_datacheckparameter
            FROM FS_DATACHECK_ITEM
            WHERE i_checkitemgrouptype = 1 AND i_isapply = 1
            ORDER BY objectid
            """
        ).fetchall()
    finally:
        connection.close()

    rules: list[dict[str, Any]] = []
    for object_id, title, parameter_text in rows:
        parameters = json.loads(parameter_text)
        target_table = parameters["MetadataDataSource"].strip()
        condition = parameters["LogicCondition"].strip()
        locator_fields = split_fields(parameters.get("MetadataFields", ""))
        required_tables = unique(
            [target_table, *REFERENCE_TABLE_PATTERN.findall(condition)]
        )
        required_fields = unique(["YD_ID", *locator_fields])
        rules.append(
            {
                "id": f"BASE_{object_id}",
                "sourceId": DEFAULT_SOURCE_ID,
                "enabled": True,
                "severity": "MANDATORY",
                "targetTable": target_table,
                "title": title.strip(),
                "explanation": f"来源于 ZDB 内置启用规则：{title.strip()}。",
                "requiredTables": required_tables,
                "requiredFields": required_fields,
                "locatorFields": locator_fields or ["YD_ID"],
                "sql": scoped_sql(target_table, condition),
            }
        )
    return rules, source_tables


def split_fields(fields: str) -> list[str]:
    return unique([field.strip() for field in fields.split(",") if field.strip()])


def unique(values: list[str]) -> list[str]:
    result: list[str] = []
    for value in values:
        if value not in result:
            result.append(value)
    return result


def scoped_sql(target_table: str, condition: str) -> str:
    safe_table = target_table.replace('"', '""')
    return (
        f'SELECT * FROM "{safe_table}" '
        f"WHERE YD_ID = :ydId AND ({condition})"
    )


def build_rule_set(rules: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "ruleSetVersion": "2026.05-baseline",
        "publishedAt": "2026-05-26",
        "sources": [
            {
                "id": DEFAULT_SOURCE_ID,
                "kind": "BASE_SNAPSHOT",
                "label": "ZDB 启用规则快照",
                "description": "从已验证 ZDB 样本中提取的启用质检规则快照。",
            }
        ],
        "rules": rules,
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Input .zdb SQLite file")
    parser.add_argument("output", type=Path, help="Output embedded rule-set JSON file")
    return parser.parse_args()


def main() -> None:
    args = parse_arguments()
    rules, _ = read_enabled_rules(args.source)
    if len(rules) != 264:
        raise RuntimeError(f"Expected 264 enabled rules, extracted {len(rules)}.")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(build_rule_set(rules), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Extracted {len(rules)} enabled rules to {args.output}.")


if __name__ == "__main__":
    main()
