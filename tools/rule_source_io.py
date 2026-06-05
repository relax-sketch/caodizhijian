#!/usr/bin/env python3
"""Shared helpers for development-time YAML rule sources."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError as exc:  # pragma: no cover - exercised by users without PyYAML.
    raise SystemExit(
        "PyYAML is required. Install it with: python -m pip install PyYAML"
    ) from exc


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = PROJECT_ROOT / "rules-src"
DEFAULT_OUTPUT_JSON = PROJECT_ROOT / "app/src/main/assets/rules/rule-set.json"

RULE_SET_FILE = "rule-set.yaml"
SOURCES_FILE = "sources/sources.yaml"

ROOT_FIELDS = ["schemaVersion", "ruleSetVersion", "publishedAt"]
SOURCE_FIELDS = ["id", "kind", "label", "description"]
RULE_FIELDS = [
    "id",
    "sourceId",
    "enabled",
    "severity",
    "targetTable",
    "title",
    "explanation",
    "requiredTables",
    "requiredFields",
    "locatorFields",
    "sql",
]

SOURCE_KINDS = {"BASE_SNAPSHOT", "ADDITIONAL"}
SEVERITIES = {"MANDATORY", "ADVISORY"}

LEADING_QUERY = re.compile(r"^\s*(SELECT|WITH)\b", re.IGNORECASE)
FORBIDDEN_SQL = re.compile(
    r"\b(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|ATTACH|DETACH|PRAGMA|VACUUM|REINDEX|TRUNCATE)\b"
    r"|\bREPLACE\s+INTO\b",
    re.IGNORECASE,
)


class LiteralString(str):
    """Marker used to dump SQL as YAML literal blocks."""


class RuleYamlDumper(yaml.SafeDumper):
    def increase_indent(self, flow: bool = False, indentless: bool = False) -> None:
        return super().increase_indent(flow, False)


def _literal_string_representer(
    dumper: yaml.SafeDumper,
    value: LiteralString,
) -> yaml.nodes.ScalarNode:
    return dumper.represent_scalar("tag:yaml.org,2002:str", value, style="|")


RuleYamlDumper.add_representer(LiteralString, _literal_string_representer)


@dataclass(frozen=True)
class RuleEntry:
    path: Path
    rule: dict[str, Any]


@dataclass(frozen=True)
class ValidationIssue:
    level: str
    rule_id: str
    path: Path
    reason: str

    def format(self, source_dir: Path) -> str:
        try:
            display_path = self.path.relative_to(PROJECT_ROOT)
        except ValueError:
            display_path = self.path
        if self.path == Path("<sources>") or self.path == Path("<rule-set>"):
            display_path = self.path
        return (
            f"{self.level}: {self.rule_id}\n"
            f"file: {display_path.as_posix()}\n"
            f"reason: {self.reason}"
        )


def load_yaml_file(path: Path) -> Any:
    try:
        with path.open("r", encoding="utf-8") as handle:
            return yaml.safe_load(handle) or {}
    except yaml.YAMLError as exc:
        raise ValueError(f"{path}: invalid YAML: {exc}") from exc


def dump_yaml_file(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        yaml.dump(
            data,
            handle,
            Dumper=RuleYamlDumper,
            allow_unicode=True,
            sort_keys=False,
            width=1000,
        )


def dump_rule_yaml_file(path: Path, rule: dict[str, Any]) -> None:
    ordered = order_rule(rule)
    ordered["sql"] = LiteralString(str(ordered["sql"]))
    dump_yaml_file(path, ordered)


def load_source_tree(source_dir: Path) -> tuple[dict[str, Any], list[dict[str, Any]], list[RuleEntry]]:
    rule_set_path = source_dir / RULE_SET_FILE
    sources_path = source_dir / SOURCES_FILE
    if not rule_set_path.is_file():
        raise FileNotFoundError(f"Missing {rule_set_path}")
    if not sources_path.is_file():
        raise FileNotFoundError(f"Missing {sources_path}")

    root = require_mapping(load_yaml_file(rule_set_path), rule_set_path)
    source_document = require_mapping(load_yaml_file(sources_path), sources_path)
    sources = source_document.get("sources")
    if not isinstance(sources, list):
        raise ValueError(f"{sources_path}: top-level 'sources' must be a list")

    rule_entries: list[RuleEntry] = []
    for rule_file in iter_rule_files(source_dir):
        rule = require_mapping(load_yaml_file(rule_file), rule_file)
        rule_entries.append(RuleEntry(path=rule_file, rule=rule))
    return root, sources, rule_entries


def iter_rule_files(source_dir: Path) -> list[Path]:
    rules_dir = source_dir / "rules"
    if not rules_dir.is_dir():
        return []
    files = []
    for path in rules_dir.rglob("*"):
        if path.suffix.lower() not in {".yaml", ".yml"}:
            continue
        if path.name.startswith("_") or any(part.startswith(".") for part in path.parts):
            continue
        files.append(path)
    return sorted(files, key=lambda item: item.as_posix().lower())


def require_mapping(value: Any, path: Path) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected a YAML mapping")
    return value


def build_embedded_rule_set(
    root: dict[str, Any],
    sources: list[dict[str, Any]],
    rule_entries: list[RuleEntry],
) -> dict[str, Any]:
    ordered_sources = [order_source(source) for source in sources]
    source_order = {
        str(source.get("id", "")): index for index, source in enumerate(ordered_sources)
    }
    ordered_rules = [order_rule(entry.rule) for entry in rule_entries]
    ordered_rules.sort(
        key=lambda rule: (
            source_order.get(str(rule.get("sourceId", "")), len(source_order)),
            natural_key(str(rule.get("id", ""))),
        )
    )
    return {
        "schemaVersion": root.get("schemaVersion"),
        "ruleSetVersion": str(root.get("ruleSetVersion", "")),
        "publishedAt": str(root.get("publishedAt", "")),
        "sources": ordered_sources,
        "rules": ordered_rules,
    }


def write_embedded_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def read_embedded_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def order_source(source: dict[str, Any]) -> dict[str, Any]:
    return {field: source.get(field) for field in SOURCE_FIELDS}


def order_rule(rule: dict[str, Any]) -> dict[str, Any]:
    return {field: rule.get(field) for field in RULE_FIELDS}


def natural_key(value: str) -> list[Any]:
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", value)]


def validate_source_tree(
    root: dict[str, Any],
    sources: list[Any],
    rule_entries: list[RuleEntry],
) -> tuple[list[ValidationIssue], list[ValidationIssue]]:
    errors: list[ValidationIssue] = []
    warnings: list[ValidationIssue] = []

    errors.extend(validate_root(root))
    source_ids: set[str] = set()
    seen_sources: dict[str, Path] = {}
    for source in sources:
        if not isinstance(source, dict):
            errors.append(issue("ERROR", "<sources>", Path("<sources>"), "Each source must be a mapping."))
            continue
        source_id = text_value(source.get("id"))
        errors.extend(validate_source(source))
        if source_id:
            if source_id in seen_sources:
                errors.append(issue("ERROR", source_id, Path("<sources>"), "Duplicate source id."))
            seen_sources[source_id] = Path("<sources>")
            source_ids.add(source_id)

    seen_rules: dict[str, Path] = {}
    for entry in rule_entries:
        rule = entry.rule
        rule_id = text_value(rule.get("id")) or "<missing id>"
        errors.extend(validate_rule_shape(rule, entry.path))
        if rule_id != "<missing id>":
            if rule_id in seen_rules:
                errors.append(
                    issue(
                        "ERROR",
                        rule_id,
                        entry.path,
                        f"Duplicate rule id also found in {seen_rules[rule_id].as_posix()}.",
                    )
                )
            seen_rules[rule_id] = entry.path
        source_id = text_value(rule.get("sourceId"))
        if source_id and source_id not in source_ids:
            errors.append(issue("ERROR", rule_id, entry.path, f"Unknown sourceId '{source_id}'."))
        target_table = text_value(rule.get("targetTable"))
        required_tables = string_list(rule.get("requiredTables"))
        if target_table and required_tables is not None and target_table not in required_tables:
            errors.append(
                issue(
                    "ERROR",
                    rule_id,
                    entry.path,
                    f"targetTable '{target_table}' must be included in requiredTables.",
                )
            )
        required_fields = string_list(rule.get("requiredFields"))
        locator_fields = string_list(rule.get("locatorFields"))
        if required_fields is not None and locator_fields is not None:
            missing_locator_fields = [
                field for field in locator_fields if field not in required_fields
            ]
            if missing_locator_fields:
                warnings.append(
                    issue(
                        "WARNING",
                        rule_id,
                        entry.path,
                        "locatorFields not present in requiredFields: "
                        + ", ".join(missing_locator_fields),
                    )
                )
        sql = rule.get("sql")
        if isinstance(sql, str):
            errors.extend(validate_sql(rule_id, entry.path, sql))

    return errors, warnings


def validate_root(root: dict[str, Any]) -> list[ValidationIssue]:
    errors: list[ValidationIssue] = []
    path = Path("<rule-set>")
    for field in ROOT_FIELDS:
        if field not in root:
            errors.append(issue("ERROR", "<rule-set>", path, f"Missing required field '{field}'."))
    unknown = sorted(set(root) - set(ROOT_FIELDS))
    if unknown:
        errors.append(
            issue("ERROR", "<rule-set>", path, "Unknown field(s): " + ", ".join(unknown))
        )
    if root.get("schemaVersion") != 1:
        errors.append(issue("ERROR", "<rule-set>", path, "schemaVersion must be 1."))
    for field in ["ruleSetVersion", "publishedAt"]:
        if not text_value(root.get(field)):
            errors.append(issue("ERROR", "<rule-set>", path, f"'{field}' must be non-blank."))
    return errors


def validate_source(source: dict[str, Any]) -> list[ValidationIssue]:
    source_id = text_value(source.get("id")) or "<missing source id>"
    path = Path("<sources>")
    errors: list[ValidationIssue] = []
    unknown = sorted(set(source) - set(SOURCE_FIELDS))
    if unknown:
        errors.append(issue("ERROR", source_id, path, "Unknown field(s): " + ", ".join(unknown)))
    for field in SOURCE_FIELDS:
        if not text_value(source.get(field)):
            errors.append(issue("ERROR", source_id, path, f"Missing or blank '{field}'."))
    kind = text_value(source.get("kind"))
    if kind and kind not in SOURCE_KINDS:
        errors.append(issue("ERROR", source_id, path, f"Unsupported kind '{kind}'."))
    return errors


def validate_rule_shape(rule: dict[str, Any], path: Path) -> list[ValidationIssue]:
    rule_id = text_value(rule.get("id")) or "<missing id>"
    errors: list[ValidationIssue] = []
    unknown = sorted(set(rule) - set(RULE_FIELDS))
    if unknown:
        errors.append(issue("ERROR", rule_id, path, "Unknown field(s): " + ", ".join(unknown)))

    for field in ["id", "sourceId", "severity", "targetTable", "title", "explanation", "sql"]:
        if not text_value(rule.get(field)):
            errors.append(issue("ERROR", rule_id, path, f"Missing or blank '{field}'."))

    if not isinstance(rule.get("enabled"), bool):
        errors.append(issue("ERROR", rule_id, path, "Missing or non-boolean 'enabled'."))

    severity = text_value(rule.get("severity"))
    if severity and severity not in SEVERITIES:
        errors.append(issue("ERROR", rule_id, path, f"Unsupported severity '{severity}'."))

    for field in ["requiredTables", "requiredFields", "locatorFields"]:
        value = rule.get(field)
        if string_list(value) is None:
            errors.append(issue("ERROR", rule_id, path, f"'{field}' must be a non-empty list of strings."))
            continue
        values = string_list(value) or []
        duplicate = first_duplicate(values)
        if duplicate:
            errors.append(issue("ERROR", rule_id, path, f"Duplicate {field} entry '{duplicate}'."))
    return errors


def validate_sql(rule_id: str, path: Path, sql: str) -> list[ValidationIssue]:
    errors: list[ValidationIssue] = []
    normalized = remove_quoted_content_and_comments(sql)
    if ":ydId" not in sql:
        errors.append(issue("ERROR", rule_id, path, "SQL must contain :ydId."))
    if not LEADING_QUERY.search(normalized):
        errors.append(issue("ERROR", rule_id, path, "SQL must start with SELECT or WITH."))
    forbidden = FORBIDDEN_SQL.search(normalized)
    if forbidden:
        errors.append(
            issue(
                "ERROR",
                rule_id,
                path,
                f"SQL contains forbidden keyword '{forbidden.group(0)}'.",
            )
        )
    if contains_multiple_statements(normalized):
        errors.append(issue("ERROR", rule_id, path, "SQL must contain a single statement."))
    return errors


def remove_quoted_content_and_comments(sql: str) -> str:
    without_comments = re.sub(r"--[^\r\n]*", " ", sql)
    without_comments = re.sub(r"/\*.*?\*/", " ", without_comments, flags=re.DOTALL)
    without_strings = re.sub(r"'(?:''|[^'])*'", "''", without_comments)
    return re.sub(r'"(?:""|[^"])*"', '""', without_strings)


def contains_multiple_statements(sql: str) -> bool:
    without_trailing = sql.strip()
    if without_trailing.endswith(";"):
        without_trailing = without_trailing[:-1].strip()
    return ";" in without_trailing


def text_value(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    return value.strip()


def string_list(value: Any) -> list[str] | None:
    if not isinstance(value, list) or not value:
        return None
    result: list[str] = []
    for item in value:
        if not isinstance(item, str) or not item.strip():
            return None
        result.append(item.strip())
    return result


def first_duplicate(values: list[str]) -> str | None:
    seen: set[str] = set()
    for value in values:
        if value in seen:
            return value
        seen.add(value)
    return None


def issue(level: str, rule_id: str, path: Path, reason: str) -> ValidationIssue:
    return ValidationIssue(level=level, rule_id=rule_id, path=path, reason=reason)


def summarize_rule_set(data: dict[str, Any]) -> dict[str, Any]:
    rules = data.get("rules") or []
    sources = data.get("sources") or []
    return {
        "schemaVersion": data.get("schemaVersion"),
        "ruleSetVersion": data.get("ruleSetVersion"),
        "publishedAt": data.get("publishedAt"),
        "sourceCount": len(sources),
        "ruleCount": len(rules),
        "sourceIds": [source.get("id") for source in sources],
        "ruleIds": [rule.get("id") for rule in rules],
    }
