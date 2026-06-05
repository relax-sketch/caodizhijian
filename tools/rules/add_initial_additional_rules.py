#!/usr/bin/env python3
"""Append SQL-converted grassland spreadsheet rules to the embedded rule set."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SOURCE_ID = "grassland-additional-20260526"


@dataclass(frozen=True)
class RuleSpec:
    source_number: int
    severity: str
    table: str
    title: str
    explanation: str
    fields: list[str]
    locator_fields: list[str]
    sql: str
    required_tables: list[str] | None = None


def select(table: str, columns: list[str], where: str) -> str:
    return f'SELECT {", ".join(columns)} FROM "{table}" WHERE {where}'


def label(field: str, alias: str) -> str:
    return f"{field} AS '{alias}'"


def is_blank(field: str) -> str:
    return f"({field} IS NULL OR TRIM(CAST({field} AS TEXT)) = '')"


def is_not_blank(field: str) -> str:
    return f"({field} IS NOT NULL AND TRIM(CAST({field} AS TEXT)) <> '')"


def number(field: str) -> str:
    return f"CAST({field} AS REAL)"


def compact_list_expr(field: str) -> str:
    return f"REPLACE(REPLACE(REPLACE(REPLACE(TRIM({field}), ',', '，'), '、', '，'), ' ', ''), '　', '')"


def main_columns(extra: list[str]) -> list[str]:
    return ["YD_ID", *extra]


def line_columns(extra: list[str]) -> list[str]:
    return ["YD_ID", "YX_ID", *extra]


def sample_columns(extra: list[str]) -> list[str]:
    return ["YD_ID", "YF_ID", *extra]


def plant_columns(name_field: str, extra: list[str]) -> list[str]:
    return ["YD_ID", "YF_ID", name_field, *extra]


def scoped_main(condition: str) -> str:
    return f"YD_ID = :ydId AND ({condition})"


def allowed_codes_expr(field: str, codes: list[str]) -> str:
    values = ", ".join(f"'{code}'" for code in codes)
    return f"{field} NOT IN ({values})"


def aggregate_count_sql(
    table: str,
    count_alias: str,
    actual_alias: str,
    condition: str,
    required_tables: list[str] | None = None,
) -> str:
    return (
        f'SELECT YD_ID, COUNT(*) AS "{actual_alias}" FROM "{table}" '
        f"WHERE YD_ID = :ydId GROUP BY YD_ID HAVING {condition}"
    )


def related_plant_count_sql(
    sample_table: str,
    plant_table: str,
    actual_alias: str,
    condition: str,
) -> str:
    return (
        f'SELECT s.YD_ID, s.YF_ID, COUNT(p.PK_UID) AS "{actual_alias}" '
        f'FROM "{sample_table}" s '
        f'LEFT JOIN "{plant_table}" p ON p.XB_GLH = s.MZGUID '
        f"WHERE s.YD_ID = :ydId "
        f"GROUP BY s.YD_ID, s.YF_ID HAVING {condition}"
    )


def transect_endpoint_distance_sql() -> str:
    distance_sq = (
        "((CAST(l.YX_X AS REAL) - CAST(p.ZUOBIAO_X AS REAL)) * "
        "(CAST(l.YX_X AS REAL) - CAST(p.ZUOBIAO_X AS REAL)) + "
        "(CAST(l.YX_Y AS REAL) - CAST(p.ZUOBIAO_Y AS REAL)) * "
        "(CAST(l.YX_Y AS REAL) - CAST(p.ZUOBIAO_Y AS REAL)))"
    )
    return (
        'SELECT l.YD_ID, l.YX_ID, l.YX_X AS "样线终点 X", l.YX_Y AS "样线终点 Y", '
        'p.ZUOBIAO_X AS "样地中心 X", p.ZUOBIAO_Y AS "样地中心 Y" '
        'FROM "YX_TRCY_TB" l JOIN "YD_TRCY_PT" p ON p.YD_ID = l.YD_ID '
        "WHERE l.YD_ID = :ydId AND l.YX_X IS NOT NULL AND l.YX_Y IS NOT NULL "
        "AND p.ZUOBIAO_X IS NOT NULL AND p.ZUOBIAO_Y IS NOT NULL "
        f"AND ({distance_sq} < 380.25 OR {distance_sq} > 420.25)"
    )


def average_height_leans_to_weighted_sql(sample_table: str, plant_table: str) -> str:
    return (
        "WITH stats AS ("
        "SELECT s.YD_ID, s.YF_ID, s.CQPJ_GD, "
        "AVG(CAST(p.H AS REAL)) AS simple_avg, "
        "SUM(CAST(p.FVC AS REAL) * CAST(p.H AS REAL)) / SUM(CAST(p.FVC AS REAL)) AS weighted_avg "
        f'FROM "{sample_table}" s '
        f'JOIN "{plant_table}" p ON p.XB_GLH = s.MZGUID '
        "WHERE s.YD_ID = :ydId AND s.CQPJ_GD IS NOT NULL AND p.H IS NOT NULL AND p.FVC IS NOT NULL "
        "GROUP BY s.YD_ID, s.YF_ID, s.CQPJ_GD "
        "HAVING COUNT(p.PK_UID) > 0 AND SUM(CAST(p.FVC AS REAL)) > 0"
        ") "
        "SELECT YD_ID, YF_ID, CQPJ_GD AS \"草群平均高度\", "
        "simple_avg AS \"植物高度普通平均值\", weighted_avg AS \"覆盖度加权平均值\" "
        "FROM stats WHERE "
        "(weighted_avg > simple_avg AND CAST(CQPJ_GD AS REAL) <= simple_avg) OR "
        "(weighted_avg < simple_avg AND CAST(CQPJ_GD AS REAL) >= simple_avg)"
    )


def weighted_recommended_height_sql(sample_table: str, plant_table: str) -> str:
    return (
        "WITH stats AS ("
        "SELECT s.YD_ID, s.YF_ID, s.CQPJ_GD, "
        "SUM(CAST(p.FVC AS REAL) / 100.0 * CAST(p.H AS REAL)) AS recommended_height "
        f'FROM "{sample_table}" s '
        f'JOIN "{plant_table}" p ON p.XB_GLH = s.MZGUID '
        "WHERE s.YD_ID = :ydId AND s.CQPJ_GD IS NOT NULL AND p.H IS NOT NULL AND p.FVC IS NOT NULL "
        "GROUP BY s.YD_ID, s.YF_ID, s.CQPJ_GD "
        "HAVING COUNT(p.PK_UID) > 0"
        ") "
        "SELECT YD_ID, YF_ID, CQPJ_GD AS \"草群平均高度\", "
        "recommended_height AS \"主要推荐高度\" "
        "FROM stats WHERE ABS(CAST(CQPJ_GD AS REAL) - recommended_height) > 0.5"
    )


def sample_total_coverage_sql(sample_table: str, plant_table: str) -> str:
    return (
        'SELECT s.YD_ID, s.YF_ID, s.ZGD AS "样方总盖度", '
        'SUM(CAST(p.FVC AS REAL)) AS "植物盖度合计" '
        f'FROM "{sample_table}" s '
        f'JOIN "{plant_table}" p ON p.XB_GLH = s.MZGUID '
        "WHERE s.YD_ID = :ydId AND s.ZGD IS NOT NULL AND p.FVC IS NOT NULL "
        "GROUP BY s.YD_ID, s.YF_ID, s.ZGD "
        "HAVING COUNT(p.PK_UID) > 0 AND CAST(s.ZGD AS REAL) > SUM(CAST(p.FVC AS REAL))"
    )


def plant_category_uniqueness_sql(table: str) -> str:
    return (
        "WITH classified AS ("
        "SELECT YD_ID, YF_ID, XB_GLH, "
        "CASE "
        "WHEN YOUSHIZHONG = '1' AND KESHI = '1' AND DUHAI = '2' THEN '优势可食' "
        "WHEN YOUSHIZHONG = '1' AND KESHI = '2' AND DUHAI = '1' THEN '优势毒害' "
        "WHEN YOUSHIZHONG = '2' AND KESHI = '1' AND DUHAI = '2' THEN '其他可食' "
        "WHEN YOUSHIZHONG = '2' AND KESHI = '2' AND DUHAI = '1' THEN '其他毒害' "
        "END AS plant_category "
        f'FROM "{table}" '
        "WHERE YD_ID = :ydId AND YOUSHIZHONG IN ('1','2') AND KESHI IN ('1','2') AND DUHAI IN ('1','2')"
        ") "
        "SELECT YD_ID, YF_ID, plant_category AS \"植物分类\", COUNT(*) AS \"重复分类数量\" "
        "FROM classified WHERE plant_category IS NOT NULL "
        "GROUP BY YD_ID, YF_ID, XB_GLH, plant_category HAVING COUNT(*) > 1"
    )


def slope_position_aspect_sql() -> str:
    return select(
        "YD_TRCY_PT",
        main_columns([label("PO_DU", "坡度"), label("PO_WEI", "坡位"), label("PO_XIANG", "坡向")]),
        scoped_main(
            "(PO_DU IS NOT NULL AND CAST(PO_DU AS REAL) < 5 "
            "AND (PO_WEI IS NULL OR PO_WEI NOT IN ('0','6') OR PO_XIANG IS NULL OR PO_XIANG <> '9')) "
            "OR (PO_DU IS NOT NULL AND CAST(PO_DU AS REAL) > 5 "
            "AND (PO_WEI IS NULL OR PO_WEI IN ('0','6') OR PO_XIANG IS NULL OR PO_XIANG = '9'))"
        ),
    )


def erosion_type_degree_sql() -> str:
    return select(
        "YD_TRCY_PT",
        main_columns([label("DB_QS_LX", "地表侵蚀类型"), label("DB_QS_CD", "地表侵蚀程度")]),
        scoped_main(
            "(DB_QS_LX = '5' AND DB_QS_CD IS NOT NULL AND TRIM(CAST(DB_QS_CD AS TEXT)) <> '') "
            "OR (DB_QS_LX <> '5' AND DB_QS_LX IS NOT NULL "
            "AND ((DB_QS_CD IS NULL OR TRIM(CAST(DB_QS_CD AS TEXT)) = '') OR DB_QS_CD = '0'))"
        ),
    )


def utilization_mode_intensity_sql() -> str:
    return select(
        "YD_TRCY_PT",
        main_columns([label("LYFS", "利用方式"), label("LYQD", "利用强度")]),
        scoped_main(
            "(LYFS = '36' AND (LYQD IS NULL OR LYQD <> '9')) "
            "OR (LYFS IS NOT NULL AND LYFS <> '' AND LYFS <> '36' AND LYQD = '9')"
        ),
    )


def measurement_dominant_plant_name_sql() -> str:
    plant_list = compact_list_expr("ZW_MC")
    dominant_list = compact_list_expr("YS_CZ")
    return (
        "WITH RECURSIVE "
        'base AS (SELECT p.YD_ID, p.YF_ID, p.ZW_MC, p.YOUSHIZHONG, yd.YS_CZ FROM "ZWDCB_CCYF_TB" p '
        'JOIN "YD_TRCY_PT" yd ON yd.YD_ID = p.YD_ID WHERE p.YD_ID = :ydId), '
        "plant_split(YD_ID, YF_ID, plant, rest) AS ("
        f'SELECT YD_ID, YF_ID, "", {plant_list} || "，" FROM base '
        'WHERE YOUSHIZHONG = "1" AND ZW_MC IS NOT NULL AND TRIM(ZW_MC) <> "" '
        'UNION ALL SELECT YD_ID, YF_ID, TRIM(SUBSTR(rest, 1, INSTR(rest, "，") - 1)), '
        'SUBSTR(rest, INSTR(rest, "，") + 1) FROM plant_split WHERE rest <> "" AND INSTR(rest, "，") > 0), '
        'plot_seed AS (SELECT DISTINCT YD_ID, YF_ID, YS_CZ FROM base WHERE YS_CZ IS NOT NULL AND TRIM(YS_CZ) <> ""), '
        "dominant_split(YD_ID, YF_ID, dominant, rest) AS ("
        f'SELECT YD_ID, YF_ID, "", {dominant_list} || "，" FROM plot_seed '
        'UNION ALL SELECT YD_ID, YF_ID, TRIM(SUBSTR(rest, 1, INSTR(rest, "，") - 1)), '
        'SUBSTR(rest, INSTR(rest, "，") + 1) FROM dominant_split WHERE rest <> "" AND INSTR(rest, "，") > 0), '
        "plot_dominants AS (SELECT YD_ID, YF_ID, "
        "CASE WHEN dominant LIKE '具%的%' THEN SUBSTR(dominant, INSTR(dominant, '的') + 1) ELSE dominant END AS dominant_plant "
        'FROM dominant_split WHERE dominant <> ""), '
        'summary AS (SELECT YD_ID, YF_ID, GROUP_CONCAT(CASE WHEN YOUSHIZHONG = "1" THEN ZW_MC END) AS "测产优势植物", '
        'YS_CZ AS "样地优势草种" FROM base GROUP BY YD_ID, YF_ID, YS_CZ), '
        "matches AS (SELECT p.YD_ID, p.YF_ID, COUNT(*) AS hit_count FROM plant_split p "
        "JOIN plot_dominants d ON d.YD_ID = p.YD_ID AND d.YF_ID = p.YF_ID AND d.dominant_plant = p.plant "
        "WHERE p.plant <> '' GROUP BY p.YD_ID, p.YF_ID) "
        'SELECT s.YD_ID, s.YF_ID, s."测产优势植物", s."样地优势草种" FROM summary s '
        "LEFT JOIN matches m ON m.YD_ID = s.YD_ID AND m.YF_ID = s.YF_ID "
        'WHERE s."测产优势植物" IS NOT NULL AND COALESCE(m.hit_count, 0) = 0'
    )


def fixture_rules() -> list[RuleSpec]:
    zcjl_zbfg_missing = " OR ".join(is_blank(f"ZCJL_ZBFG_{index:02d}") for index in range(1, 21))
    zcjl_lxlb_missing = " OR ".join(is_blank(f"ZCJL_LXLB_{index:02d}") for index in range(1, 21))
    zcjl_zbfg_fields = [f"ZCJL_ZBFG_{index:02d}" for index in range(1, 21)]
    zcjl_lxlb_fields = [f"ZCJL_LXLB_{index:02d}" for index in range(1, 21)]

    return [
        RuleSpec(
            1,
            "MANDATORY",
            "YD_TRCY_PT",
            "已调查样地关键字段未填写完整",
            "调查状态为已调查时，省、市、县、乡、村、海拔、地貌、坡度、坡位、坡向、土壤质地、土层厚度必须填写。",
            ["YD_ID", "DC_ZT", "SHENG", "SHI", "XIAN", "XIANG", "CUN", "HAI_BA", "DI_MAO", "PO_DU", "PO_WEI", "PO_XIANG", "TU_RANG_ZD", "TU_CENG_HD"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns(
                    [
                        label("DC_ZT", "调查状态"),
                        label("SHENG", "省"),
                        label("SHI", "市"),
                        label("XIAN", "县"),
                        label("XIANG", "乡"),
                        label("CUN", "村"),
                        label("HAI_BA", "海拔"),
                        label("DI_MAO", "地貌"),
                        label("PO_DU", "坡度"),
                        label("PO_WEI", "坡位"),
                        label("PO_XIANG", "坡向"),
                        label("TU_RANG_ZD", "土壤质地"),
                        label("TU_CENG_HD", "土层厚度"),
                    ]
                ),
                scoped_main(
                    "DC_ZT = '1' AND ("
                    + " OR ".join(
                        is_blank(field)
                        for field in [
                            "SHENG",
                            "SHI",
                            "XIAN",
                            "XIANG",
                            "CUN",
                            "HAI_BA",
                            "DI_MAO",
                            "PO_DU",
                            "PO_WEI",
                            "PO_XIANG",
                            "TU_RANG_ZD",
                            "TU_CENG_HD",
                        ]
                    )
                    + ")"
                ),
            ),
        ),
        RuleSpec(
            2,
            "MANDATORY",
            "YD_TRCY_PT",
            "天然草原样地面积不是 0.125",
            "天然草原样地面积统一填写为 0.125。",
            ["YD_ID", "MIAN_JI"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("MIAN_JI", "面积")]),
                scoped_main(f"{is_blank('MIAN_JI')} OR {number('MIAN_JI')} <> 0.125"),
            ),
        ),
        RuleSpec(
            3,
            "MANDATORY",
            "YD_TRCY_PT",
            "中心桩 GNSS 坐标 X 位数异常",
            "中心桩 GNSS 坐标 X（米）的整数部分必须为 8 位。",
            ["YD_ID", "ZUOBIAO_X"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("ZUOBIAO_X", "中心桩 GNSS 坐标 X")]),
                scoped_main(f"{is_blank('ZUOBIAO_X')} OR LENGTH(CAST(ROUND(ABS(ZUOBIAO_X), 0) AS INTEGER)) <> 8"),
            ),
        ),
        RuleSpec(
            4,
            "MANDATORY",
            "YD_TRCY_PT",
            "中心桩 GNSS 坐标 Y 位数异常",
            "中心桩 GNSS 坐标 Y（米）的整数部分必须为 7 位。",
            ["YD_ID", "ZUOBIAO_Y"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("ZUOBIAO_Y", "中心桩 GNSS 坐标 Y")]),
                scoped_main(f"{is_blank('ZUOBIAO_Y')} OR LENGTH(CAST(ROUND(ABS(ZUOBIAO_Y), 0) AS INTEGER)) <> 7"),
            ),
        ),
        RuleSpec(
            5,
            "ADVISORY",
            "YD_TRCY_PT",
            "草地类不在湖北允许范围",
            "草地类只能填写为热性草丛、热性灌草丛、低地草甸、山地草甸、暖性草丛、暖性灌草丛。",
            ["YD_ID", "CD_L"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("CD_L", "草地类")]),
                scoped_main(f"{is_blank('CD_L')} OR {allowed_codes_expr('CD_L', ['8', '9', '14', '15', '16', '17'])}"),
            ),
        ),
        RuleSpec(
            6,
            "ADVISORY",
            "YD_TRCY_PT",
            "草地型与草地类关系需核对",
            "草地型应与草地类对应；草地型填写为其他或为空时，其他草地型字段必须填写。",
            ["YD_ID", "CD_L", "CD_XING", "CD_XING_QT"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("CD_L", "草地类"), label("CD_XING", "草地型"), label("CD_XING_QT", "其他草地型")]),
                scoped_main(f"{is_blank('CD_XING')} OR (CD_XING = '826' AND {is_blank('CD_XING_QT')})"),
            ),
        ),
        RuleSpec(
            7,
            "MANDATORY",
            "YD_TRCY_PT",
            "草地起源不是天然草原",
            "天然草原样地的草地起源统一填写为天然草原。",
            ["YD_ID", "CD_LB"],
            ["YD_ID"],
            select("YD_TRCY_PT", main_columns([label("CD_LB", "草地起源")]), scoped_main(f"{is_blank('CD_LB')} OR CD_LB <> '1'")),
        ),
        RuleSpec(
            8,
            "MANDATORY",
            "YD_TRCY_PT",
            "植被结构未填写或与覆盖类型不对应",
            "植被结构必填，且应与植被覆盖类型对应。",
            ["YD_ID", "ZB_JG", "VEGECOVER_TYPE"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("ZB_JG", "植被结构"), label("VEGECOVER_TYPE", "植被覆盖类型")]),
                scoped_main(f"{is_blank('ZB_JG')} OR (VEGECOVER_TYPE = '2100' AND ZB_JG <> '1') OR (VEGECOVER_TYPE = '2200' AND ZB_JG <> '2')"),
            ),
        ),
        RuleSpec(
            9,
            "MANDATORY",
            "YD_TRCY_PT",
            "植被覆盖类型未填写或与植被结构不对应",
            "植被覆盖类型必填，且应与植被结构对应。",
            ["YD_ID", "VEGECOVER_TYPE", "ZB_JG"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("VEGECOVER_TYPE", "植被覆盖类型"), label("ZB_JG", "植被结构")]),
                scoped_main(f"{is_blank('VEGECOVER_TYPE')} OR (ZB_JG = '1' AND VEGECOVER_TYPE <> '2100') OR (ZB_JG = '2' AND VEGECOVER_TYPE <> '2200')"),
            ),
        ),
        RuleSpec(
            10,
            "MANDATORY",
            "YD_TRCY_PT",
            "草地型与草地类不对应",
            "天然草原样地草原型应与草原类对应；当前规则先检查草地型为空或其他草地型说明缺失。",
            ["YD_ID", "CD_L", "CD_XING", "CD_XING_QT"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("CD_L", "草地类"), label("CD_XING", "草地型"), label("CD_XING_QT", "其他草地型")]),
                scoped_main(f"{is_blank('CD_L')} OR {is_blank('CD_XING')} OR (CD_XING = '826' AND {is_blank('CD_XING_QT')})"),
            ),
        ),
        RuleSpec(11, "ADVISORY", "YD_TRCY_PT", "植被盖度低于 60%", "植被盖度小于 60% 时提示人工检查核对。", ["YD_ID", "CDGD"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("CDGD", "植被盖度")]), scoped_main("CDGD IS NOT NULL AND CAST(CDGD AS REAL) < 60"))),
        RuleSpec(12, "ADVISORY", "YD_TRCY_PT", "草群平均高度超出 5 至 100", "草群平均高度低于 5 或高于 100 时提示人工检查核对。", ["YD_ID", "ZBGD"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("ZBGD", "草群平均高度")]), scoped_main("ZBGD IS NOT NULL AND (CAST(ZBGD AS REAL) < 5 OR CAST(ZBGD AS REAL) > 100)"))),
        RuleSpec(13, "ADVISORY", "YD_TRCY_PT", "单位面积鲜草产量超过 8000", "单位面积鲜草产量超过 8000 时提示人工检查核对。", ["YD_ID", "XC_CL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("XC_CL", "单位面积鲜草产量")]), scoped_main("XC_CL IS NOT NULL AND CAST(XC_CL AS REAL) > 8000"))),
        RuleSpec(14, "ADVISORY", "YD_TRCY_PT", "单位面积干草产量超过 4000", "单位面积干草产量超过 4000 时提示人工检查核对。", ["YD_ID", "GC_CL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("GC_CL", "单位面积干草产量")]), scoped_main("GC_CL IS NOT NULL AND CAST(GC_CL AS REAL) > 4000"))),
        RuleSpec(
            15,
            "ADVISORY",
            "YD_TRCY_PT",
            "GNSS 坐标与经纬度坐标需核对",
            "样地中心点 GNSS 坐标与样地中心点经纬度坐标相差较大时提示检查核对；当前以两类坐标是否完整作为可执行校验。",
            ["YD_ID", "ZUOBIAO_X", "ZUOBIAO_Y", "ZUOBIAO_E", "ZUOBIAO_N"],
            ["YD_ID"],
            select(
                "YD_TRCY_PT",
                main_columns([label("ZUOBIAO_X", "GNSS X"), label("ZUOBIAO_Y", "GNSS Y"), label("ZUOBIAO_E", "经度"), label("ZUOBIAO_N", "纬度")]),
                scoped_main("ZUOBIAO_X IS NOT NULL AND ZUOBIAO_Y IS NOT NULL AND (ZUOBIAO_E IS NULL OR ZUOBIAO_N IS NULL)"),
            ),
        ),
        RuleSpec(16, "ADVISORY", "YD_TRCY_PT", "鲜草产量与干草产量比值超出 1 至 4", "单位面积鲜草产量/单位面积干草产量超出 1 至 4 时提示人工检查核对。", ["YD_ID", "XC_CL", "GC_CL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("XC_CL", "单位面积鲜草产量"), label("GC_CL", "单位面积干草产量")]), scoped_main("XC_CL IS NOT NULL AND GC_CL IS NOT NULL AND CAST(GC_CL AS REAL) <> 0 AND (CAST(XC_CL AS REAL) / CAST(GC_CL AS REAL) < 1 OR CAST(XC_CL AS REAL) / CAST(GC_CL AS REAL) > 4)"))),
        RuleSpec(17, "ADVISORY", "YD_TRCY_PT", "其他草地型命名需核对", "草地型为其他时，草地型命名应为优势草种加“型”。", ["YD_ID", "CD_XING", "CD_XING_QT", "YS_CZ"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("CD_XING", "草地型"), label("CD_XING_QT", "其他草地型"), label("YS_CZ", "优势草种")]), scoped_main("CD_XING = '826' AND (CD_XING_QT IS NULL OR YS_CZ IS NULL OR CD_XING_QT NOT LIKE '%' || YS_CZ || '%')"))),
        RuleSpec(18, "ADVISORY", "YD_TRCY_PT", "裸斑面积比例超过 40%", "裸斑面积比例超过 40% 时提示人工检查核对。", ["YD_ID", "LB_BL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("LB_BL", "裸斑面积比例")]), scoped_main("LB_BL IS NOT NULL AND CAST(LB_BL AS REAL) > 40"))),
        RuleSpec(19, "ADVISORY", "YD_TRCY_PT", "砾石覆盖面积比例超过 20%", "砾石覆盖面积比例超过 20% 时提示人工检查核对。", ["YD_ID", "LSFG_BL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("LSFG_BL", "砾石覆盖面积比例")]), scoped_main("LSFG_BL IS NOT NULL AND CAST(LSFG_BL AS REAL) > 20"))),
        RuleSpec(20, "ADVISORY", "YD_TRCY_PT", "盐碱斑块面积比例超过 20%", "盐碱斑块面积比例超过 20% 时提示人工检查核对。", ["YD_ID", "YJB_BL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("YJB_BL", "盐碱斑块面积比例")]), scoped_main("YJB_BL IS NOT NULL AND CAST(YJB_BL AS REAL) > 20"))),
        RuleSpec(21, "ADVISORY", "YD_TRCY_PT", "覆沙厚度超过 10 厘米", "覆沙厚度超过 10 厘米时提示人工检查核对。", ["YD_ID", "FS_HD"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("FS_HD", "覆沙厚度")]), scoped_main("FS_HD IS NOT NULL AND CAST(FS_HD AS REAL) > 10"))),
        RuleSpec(22, "ADVISORY", "YD_TRCY_PT", "单位面积鲜草产量大于 20000", "天然草原样地单位面积鲜草产量大于 20000 时，请核实测产样方调查数据。", ["YD_ID", "XC_CL"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("XC_CL", "单位面积鲜草产量")]), scoped_main("XC_CL IS NOT NULL AND CAST(XC_CL AS REAL) > 20000"))),
        RuleSpec(23, "MANDATORY", "YD_TRCY_PT", "坡度与坡向坡位关系异常", "坡度小于 5 度时，坡位应为无坡或平地，坡向应为无坡向；坡度大于 5 度时，必须填写具体坡向及具体坡位。", ["YD_ID", "PO_DU", "PO_WEI", "PO_XIANG"], ["YD_ID"], slope_position_aspect_sql()),
        RuleSpec(24, "MANDATORY", "YD_TRCY_PT", "地表侵蚀类型与程度关系异常", "地表侵蚀类型为无侵蚀时，地表侵蚀程度应为空；地表侵蚀类型为有侵蚀时，侵蚀程度不能为空或无侵蚀。", ["YD_ID", "DB_QS_LX", "DB_QS_CD"], ["YD_ID"], erosion_type_degree_sql()),
        RuleSpec(25, "MANDATORY", "YD_TRCY_PT", "利用方式与利用强度关系异常", "利用方式为其他时，利用强度必须为未利用；利用方式为有利用时，利用强度不能为未利用。", ["YD_ID", "LYFS", "LYQD"], ["YD_ID"], utilization_mode_intensity_sql()),
        RuleSpec(26, "MANDATORY", "YD_TRCY_PT", "划区轮牧不是否", "划区轮牧统一填写为否。", ["YD_ID", "HQLM"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("HQLM", "划区轮牧")]), scoped_main(f"{is_blank('HQLM')} OR HQLM <> '2'"))),
        RuleSpec(27, "MANDATORY", "YD_TRCY_PT", "基本草原不是未划定", "基本草原统一填写为未划定。", ["YD_ID", "JBCYQK"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("JBCYQK", "基本草原")]), scoped_main(f"{is_blank('JBCYQK')} OR JBCYQK <> '3'"))),
        RuleSpec(28, "MANDATORY", "YD_TRCY_PT", "样地照片未满足四类要求", "近景、远景、土壤、中心桩每个类型至少一张照片。", ["YD_ID", "ZXZ_ZP", "YJ_ZP", "JJ_ZP", "TR_ZP"], ["YD_ID"], select("YD_TRCY_PT", main_columns([label("ZXZ_ZP", "中心桩照片"), label("YJ_ZP", "远景照"), label("JJ_ZP", "近景照"), label("TR_ZP", "土壤照片")]), scoped_main(" OR ".join(is_blank(field) for field in ["ZXZ_ZP", "YJ_ZP", "JJ_ZP", "TR_ZP"])))),
        RuleSpec(29, "MANDATORY", "YX_TRCY_TB", "样线长度不是 20 米", "样线长度统一填写为 20 米。", ["YD_ID", "YX_ID", "YX_CD"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_CD", "样线长度")]), scoped_main(f"{is_blank('YX_CD')} OR {number('YX_CD')} <> 20"))),
        RuleSpec(30, "MANDATORY", "YX_TRCY_TB", "样线终点 GNSS 坐标 X 位数异常", "样线终点 GNSS 坐标 X（米）的整数部分必须为 8 位。", ["YD_ID", "YX_ID", "YX_X"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_X", "终点 GNSS 坐标 X")]), scoped_main(f"{is_blank('YX_X')} OR LENGTH(CAST(ROUND(ABS(YX_X), 0) AS INTEGER)) <> 8"))),
        RuleSpec(31, "MANDATORY", "YX_TRCY_TB", "样线终点 GNSS 坐标 Y 位数异常", "样线终点 GNSS 坐标 Y（米）的整数部分必须为 7 位。", ["YD_ID", "YX_ID", "YX_Y"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_Y", "终点 GNSS 坐标 Y")]), scoped_main(f"{is_blank('YX_Y')} OR LENGTH(CAST(ROUND(ABS(YX_Y), 0) AS INTEGER)) <> 7"))),
        RuleSpec(32, "ADVISORY", "YX_TRCY_TB", "样线盖度低于 60", "样线盖度低于 60 时提示人工检查核对。", ["YD_ID", "YX_ID", "YX_GD"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_GD", "样线盖度")]), scoped_main("YX_GD IS NOT NULL AND CAST(YX_GD AS REAL) < 60"))),
        RuleSpec(33, "ADVISORY", "YX_TRCY_TB", "样线盖度与样地盖度差值超过 20%", "样线盖度与样地盖度差值超过 20% 时提示人工检查核对。", ["YD_ID", "YX_ID", "YX_GD"], ["YD_ID", "YX_ID"], "SELECT l.YD_ID, l.YX_ID, l.YX_GD AS '样线盖度', p.CDGD AS '样地盖度' FROM \"YX_TRCY_TB\" l JOIN \"YD_TRCY_PT\" p ON p.YD_ID = l.YD_ID WHERE l.YD_ID = :ydId AND l.YX_GD IS NOT NULL AND p.CDGD IS NOT NULL AND ABS(CAST(l.YX_GD AS REAL) - CAST(p.CDGD AS REAL)) > 20", ["YX_TRCY_TB", "YD_TRCY_PT"]),
        RuleSpec(34, "MANDATORY", "YX_TRCY_TB", "样线盖度与裸斑面积比例之和不是 100%", "样线盖度与裸斑面积比例之和必须为 100%。", ["YD_ID", "YX_ID", "YX_GD", "YX_LBMJBL"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_GD", "样线盖度"), label("YX_LBMJBL", "裸斑面积比例")]), scoped_main("YX_GD IS NULL OR YX_LBMJBL IS NULL OR ABS((CAST(YX_GD AS REAL) + CAST(YX_LBMJBL AS REAL)) - 100) > 0.0001"))),
        RuleSpec(35, "MANDATORY", "YX_TRCY_TB", "样线方位角为空或夹角异常", "样线方位角不能为空，且同一样地 3 条样线间夹角应约为 120 度。", ["YD_ID", "YX_ID", "YX_FWJ"], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_FWJ", "样线方位角")]), scoped_main(is_blank("YX_FWJ")))),
        RuleSpec(36, "MANDATORY", "YX_TRCY_TB", "1-20 号记录植被覆盖存在空值", "样线 1-20 号记录植被覆盖不能为空。", ["YD_ID", "YX_ID", *zcjl_zbfg_fields], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_ID", "样线编号")]), scoped_main(zcjl_zbfg_missing))),
        RuleSpec(37, "MANDATORY", "YX_TRCY_TB", "1-20 号记录连续裸斑存在空值", "样线 1-20 号记录连续裸斑不能为空。", ["YD_ID", "YX_ID", *zcjl_lxlb_fields], ["YD_ID", "YX_ID"], select("YX_TRCY_TB", line_columns([label("YX_ID", "样线编号")]), scoped_main(zcjl_lxlb_missing))),
        RuleSpec(38, "MANDATORY", "YX_TRCY_TB", "样线照片少于 2 张", "每条样线照片至少 2 张。", ["YD_ID", "YX_ID", "MZGUID"], ["YD_ID", "YX_ID"], 'SELECT l.YD_ID, l.YX_ID, COUNT(d.PK_UID) AS "样线照片数量" FROM "YX_TRCY_TB" l LEFT JOIN "FS_DOCUMENT" d ON d.main_body_table_id = "YX_TRCY_TB" AND (d.main_body_guid LIKE l.MZGUID || "%" OR d.adjunct_path LIKE "%" || l.MZGUID || "%") WHERE l.YD_ID = :ydId GROUP BY l.YD_ID, l.YX_ID HAVING COUNT(d.PK_UID) < 2', ["YX_TRCY_TB", "FS_DOCUMENT"]),
        RuleSpec(
            39,
            "MANDATORY",
            "YX_TRCY_TB",
            "样线终点与样地中心 GNSS 距离异常",
            "样线终点到样地中心桩距离应为 20 米，19.5 至 20.5 米视为合格。",
            ["YD_ID", "YX_ID", "YX_X", "YX_Y"],
            ["YD_ID", "YX_ID"],
            transect_endpoint_distance_sql(),
            ["YX_TRCY_TB", "YD_TRCY_PT"],
        ),
        RuleSpec(40, "MANDATORY", "YF_TRCYCC_TB", "测产样方不足 3 个", "一个样地必须有 3 个测产样方。", ["YD_ID"], ["YD_ID"], aggregate_count_sql("YF_TRCYCC_TB", "count", "测产样方数量", "COUNT(*) <> 3")),
        RuleSpec(41, "ADVISORY", "YF_TRCYCC_TB", "测产样方盖度低于 60%", "测产样方盖度低于 60% 时提示人工检查核对。", ["YD_ID", "YF_ID", "ZGD"], ["YD_ID", "YF_ID"], select("YF_TRCYCC_TB", sample_columns([label("ZGD", "测产样方盖度")]), scoped_main("ZGD IS NOT NULL AND CAST(ZGD AS REAL) < 60"))),
        RuleSpec(42, "ADVISORY", "YF_TRCYCC_TB", "测产样方盖度与样地盖度差值超过 20%", "测产样方盖度与样地盖度差值超过 20% 时提示人工检查核对。", ["YD_ID", "YF_ID", "ZGD"], ["YD_ID", "YF_ID"], 'SELECT s.YD_ID, s.YF_ID, s.ZGD AS "测产样方盖度", p.CDGD AS "样地盖度" FROM "YF_TRCYCC_TB" s JOIN "YD_TRCY_PT" p ON p.YD_ID = s.YD_ID WHERE s.YD_ID = :ydId AND s.ZGD IS NOT NULL AND p.CDGD IS NOT NULL AND ABS(CAST(s.ZGD AS REAL) - CAST(p.CDGD AS REAL)) > 20', ["YF_TRCYCC_TB", "YD_TRCY_PT"]),
        RuleSpec(43, "ADVISORY", "YF_TRCYCC_TB", "测产样方草群平均高度超出 5 至 100", "测产样方草群平均高度低于 5 或高于 100 时提示人工检查核对。", ["YD_ID", "YF_ID", "CQPJ_GD"], ["YD_ID", "YF_ID"], select("YF_TRCYCC_TB", sample_columns([label("CQPJ_GD", "草群平均高度")]), scoped_main("CQPJ_GD IS NOT NULL AND (CAST(CQPJ_GD AS REAL) < 5 OR CAST(CQPJ_GD AS REAL) > 100)"))),
        RuleSpec(44, "ADVISORY", "YF_TRCYCC_TB", "测产样方植物种数少于 3 种", "测产样方植物种数少于 3 种时提示检查。", ["YD_ID", "YF_ID", "ZWZS"], ["YD_ID", "YF_ID"], select("YF_TRCYCC_TB", sample_columns([label("ZWZS", "植物种数")]), scoped_main("ZWZS IS NOT NULL AND CAST(ZWZS AS REAL) < 3"))),
        RuleSpec(45, "ADVISORY", "YF_TRCYCC_TB", "测产样方植物调查表少于 2 条", "一个测产样方下至少 2 个植物调查表。", ["YD_ID", "YF_ID", "MZGUID"], ["YD_ID", "YF_ID"], related_plant_count_sql("YF_TRCYCC_TB", "ZWDCB_CCYF_TB", "测产植物调查数量", "COUNT(p.PK_UID) < 2"), ["YF_TRCYCC_TB", "ZWDCB_CCYF_TB"]),
        RuleSpec(
            46,
            "MANDATORY",
            "YF_TRCYCC_TB",
            "测产样方草群平均高未偏向高覆盖度植物",
            "测产样方草群平均高应相对普通平均值偏向覆盖度更高的植物高度一方。",
            ["YD_ID", "YF_ID", "CQPJ_GD", "MZGUID"],
            ["YD_ID", "YF_ID"],
            average_height_leans_to_weighted_sql("YF_TRCYCC_TB", "ZWDCB_CCYF_TB"),
            ["YF_TRCYCC_TB", "ZWDCB_CCYF_TB"],
        ),
        RuleSpec(47, "MANDATORY", "YF_TRCYCC_TB", "测产样方总盖度大于植物盖度合计", "样方总盖度不应大于植物调查分表盖度值之和。", ["YD_ID", "YF_ID", "ZGD", "MZGUID"], ["YD_ID", "YF_ID"], sample_total_coverage_sql("YF_TRCYCC_TB", "ZWDCB_CCYF_TB"), ["YF_TRCYCC_TB", "ZWDCB_CCYF_TB"]),
        RuleSpec(48, "MANDATORY", "ZWDCB_CCYF_TB", "测产植物产草量鲜重不大于干重", "测产样方植物调查表中产草量鲜重必须大于产草量干重。", ["YD_ID", "YF_ID", "ZW_MC", "CCL_XIAN", "CCL_GAN"], ["YD_ID", "YF_ID", "ZW_MC"], select("ZWDCB_CCYF_TB", plant_columns("ZW_MC", [label("CCL_XIAN", "产草量鲜重"), label("CCL_GAN", "产草量干重")]), scoped_main("CCL_XIAN IS NOT NULL AND CCL_GAN IS NOT NULL AND CAST(CCL_XIAN AS REAL) <= CAST(CCL_GAN AS REAL)"))),
        RuleSpec(49, "MANDATORY", "ZWDCB_CCYF_TB", "测产植物是否可食与是否毒害关系异常", "测产样方植物调查是否可食和是否毒害不能同时为是或同时为否。", ["YD_ID", "YF_ID", "ZW_MC", "KESHI", "DUHAI"], ["YD_ID", "YF_ID", "ZW_MC"], select("ZWDCB_CCYF_TB", plant_columns("ZW_MC", [label("KESHI", "是否可食"), label("DUHAI", "是否毒害")]), scoped_main("KESHI = DUHAI AND KESHI IN ('1','2')"))),
        RuleSpec(
            50,
            "MANDATORY",
            "ZWDCB_CCYF_TB",
            "测产植物分类重复",
            "同一测产样方内植物分类最多为优势可食、优势毒害、其他可食、其他毒害四类，且分类不可重复。",
            ["YD_ID", "YF_ID", "XB_GLH", "YOUSHIZHONG", "KESHI", "DUHAI"],
            ["YD_ID", "YF_ID"],
            plant_category_uniqueness_sql("ZWDCB_CCYF_TB"),
        ),
        RuleSpec(51, "MANDATORY", "ZWDCB_CCYF_TB", "测产植物照片数量不足", "植物名称写了几个，相应植物调查表下就要有几张照片。", ["YD_ID", "YF_ID", "ZW_MC", "MZGUID"], ["YD_ID", "YF_ID", "ZW_MC"], 'SELECT p.YD_ID, p.YF_ID, p.ZW_MC, COUNT(d.PK_UID) AS "植物照片数量" FROM "ZWDCB_CCYF_TB" p LEFT JOIN "FS_DOCUMENT" d ON d.main_body_table_id = "ZWDCB_CCYF_TB" AND (d.main_body_guid LIKE p.MZGUID || "%" OR d.adjunct_path LIKE "%" || p.MZGUID || "%") WHERE p.YD_ID = :ydId GROUP BY p.YD_ID, p.YF_ID, p.ZW_MC, p.MZGUID HAVING COUNT(d.PK_UID) < 1', ["ZWDCB_CCYF_TB", "FS_DOCUMENT"]),
        RuleSpec(52, "ADVISORY", "ZWDCB_CCYF_TB", "测产优势植物鲜重占比低于 60%", "测产样方优势植物产草量鲜重之和小于测产样方合计鲜重的 60% 时提示核对。", ["YD_ID", "YF_ID", "YOUSHIZHONG", "CCL_XIAN"], ["YD_ID", "YF_ID"], 'SELECT s.YD_ID, s.YF_ID, SUM(CASE WHEN p.YOUSHIZHONG = "1" THEN CAST(p.CCL_XIAN AS REAL) ELSE 0 END) AS "优势植物鲜重和", s.HJ_XZ AS "样方合计鲜重" FROM "YF_TRCYCC_TB" s JOIN "ZWDCB_CCYF_TB" p ON p.XB_GLH = s.MZGUID WHERE s.YD_ID = :ydId GROUP BY s.YD_ID, s.YF_ID, s.HJ_XZ HAVING s.HJ_XZ IS NOT NULL AND SUM(CASE WHEN p.YOUSHIZHONG = "1" THEN CAST(p.CCL_XIAN AS REAL) ELSE 0 END) < CAST(s.HJ_XZ AS REAL) * 0.6', ["YF_TRCYCC_TB", "ZWDCB_CCYF_TB"]),
        RuleSpec(53, "ADVISORY", "ZWDCB_CCYF_TB", "测产优势植物名称与样地优势草种不一致", "测产样方优势种为是的植物名称至少有一项应与样地调查表优势草种一致；植物名称和优势草种均按逗号、顿号切分，并兼容样地优势草种中“具xxx的”前缀写法。", ["YD_ID", "YF_ID", "ZW_MC", "YOUSHIZHONG"], ["YD_ID", "YF_ID"], measurement_dominant_plant_name_sql(), ["ZWDCB_CCYF_TB", "YD_TRCY_PT"]),
        RuleSpec(54, "MANDATORY", "YF_TRCYGC_TB", "观测样方不足 3 个", "一个样地必须有 3 个观测样方。", ["YD_ID"], ["YD_ID"], aggregate_count_sql("YF_TRCYGC_TB", "count", "观测样方数量", "COUNT(*) <> 3")),
        RuleSpec(55, "ADVISORY", "YF_TRCYGC_TB", "观测样方盖度低于 60%", "观测样方盖度低于 60% 时提示人工检查核对。", ["YD_ID", "YF_ID", "ZGD"], ["YD_ID", "YF_ID"], select("YF_TRCYGC_TB", sample_columns([label("ZGD", "观测样方盖度")]), scoped_main("ZGD IS NOT NULL AND CAST(ZGD AS REAL) < 60"))),
        RuleSpec(56, "ADVISORY", "YF_TRCYGC_TB", "观测样方盖度与样地盖度差值超过 20%", "观测样方盖度与样地盖度差值超过 20% 时提示人工检查核对。", ["YD_ID", "YF_ID", "ZGD"], ["YD_ID", "YF_ID"], 'SELECT s.YD_ID, s.YF_ID, s.ZGD AS "观测样方盖度", p.CDGD AS "样地盖度" FROM "YF_TRCYGC_TB" s JOIN "YD_TRCY_PT" p ON p.YD_ID = s.YD_ID WHERE s.YD_ID = :ydId AND s.ZGD IS NOT NULL AND p.CDGD IS NOT NULL AND ABS(CAST(s.ZGD AS REAL) - CAST(p.CDGD AS REAL)) > 20', ["YF_TRCYGC_TB", "YD_TRCY_PT"]),
        RuleSpec(57, "ADVISORY", "YF_TRCYGC_TB", "观测样方草群平均高度超出 5 至 100", "观测样方草群平均高度低于 5 或高于 100 时提示人工检查核对。", ["YD_ID", "YF_ID", "CQPJ_GD"], ["YD_ID", "YF_ID"], select("YF_TRCYGC_TB", sample_columns([label("CQPJ_GD", "草群平均高度")]), scoped_main("CQPJ_GD IS NOT NULL AND (CAST(CQPJ_GD AS REAL) < 5 OR CAST(CQPJ_GD AS REAL) > 100)"))),
        RuleSpec(58, "ADVISORY", "YF_TRCYGC_TB", "观测样方植物种数少于 3 种", "观测样方植物种数少于 3 种时提示检查。", ["YD_ID", "YF_ID", "ZWZS"], ["YD_ID", "YF_ID"], select("YF_TRCYGC_TB", sample_columns([label("ZWZS", "植物种数")]), scoped_main("ZWZS IS NOT NULL AND CAST(ZWZS AS REAL) < 3"))),
        RuleSpec(59, "ADVISORY", "YF_TRCYGC_TB", "观测样方植物调查表少于 2 条", "一个观测样方下至少 2 个植物调查表。", ["YD_ID", "YF_ID", "MZGUID"], ["YD_ID", "YF_ID"], related_plant_count_sql("YF_TRCYGC_TB", "ZWDCB_GCYF_TB", "观测植物调查数量", "COUNT(p.PK_UID) < 2"), ["YF_TRCYGC_TB", "ZWDCB_GCYF_TB"]),
        RuleSpec(
            60,
            "MANDATORY",
            "YF_TRCYGC_TB",
            "观测样方草群平均高未偏向高覆盖度植物",
            "观测样方草群平均高应相对普通平均值偏向覆盖度更高的植物高度一方。",
            ["YD_ID", "YF_ID", "CQPJ_GD", "MZGUID"],
            ["YD_ID", "YF_ID"],
            average_height_leans_to_weighted_sql("YF_TRCYGC_TB", "ZWDCB_GCYF_TB"),
            ["YF_TRCYGC_TB", "ZWDCB_GCYF_TB"],
        ),
        RuleSpec(61, "MANDATORY", "YF_TRCYGC_TB", "观测样方总盖度大于植物盖度合计", "样方总盖度不应大于植物调查分表盖度值之和。", ["YD_ID", "YF_ID", "ZGD", "MZGUID"], ["YD_ID", "YF_ID"], sample_total_coverage_sql("YF_TRCYGC_TB", "ZWDCB_GCYF_TB"), ["YF_TRCYGC_TB", "ZWDCB_GCYF_TB"]),
        RuleSpec(62, "MANDATORY", "ZWDCB_GCYF_TB", "观测植物高度或盖度未正向填写", "观测样方植物调查表没有产草量字段，当前校验植物高度和盖度必须有效填写。", ["YD_ID", "YF_ID", "ZW_MC", "H", "FVC"], ["YD_ID", "YF_ID", "ZW_MC"], select("ZWDCB_GCYF_TB", plant_columns("ZW_MC", [label("H", "高度"), label("FVC", "盖度")]), scoped_main("H IS NULL OR FVC IS NULL OR CAST(H AS REAL) <= 0 OR CAST(FVC AS REAL) <= 0"))),
        RuleSpec(63, "MANDATORY", "ZWDCB_GCYF_TB", "观测植物是否可食与是否毒害关系异常", "观测样方植物调查是否可食和是否毒害不能同时为是或同时为否。", ["YD_ID", "YF_ID", "ZW_MC", "KESHI", "DUHAI"], ["YD_ID", "YF_ID", "ZW_MC"], select("ZWDCB_GCYF_TB", plant_columns("ZW_MC", [label("KESHI", "是否可食"), label("DUHAI", "是否毒害")]), scoped_main("KESHI = DUHAI AND KESHI IN ('1','2')"))),
        RuleSpec(
            64,
            "MANDATORY",
            "ZWDCB_GCYF_TB",
            "观测植物分类重复",
            "同一观测样方内植物分类最多为优势可食、优势毒害、其他可食、其他毒害四类，且分类不可重复。",
            ["YD_ID", "YF_ID", "XB_GLH", "YOUSHIZHONG", "KESHI", "DUHAI"],
            ["YD_ID", "YF_ID"],
            plant_category_uniqueness_sql("ZWDCB_GCYF_TB"),
        ),
        RuleSpec(65, "MANDATORY", "ZWDCB_GCYF_TB", "观测植物照片数量不足", "植物名称写了几个，相应植物调查表下就要有几张照片。", ["YD_ID", "YF_ID", "ZW_MC", "MZGUID"], ["YD_ID", "YF_ID", "ZW_MC"], 'SELECT p.YD_ID, p.YF_ID, p.ZW_MC, COUNT(d.PK_UID) AS "植物照片数量" FROM "ZWDCB_GCYF_TB" p LEFT JOIN "FS_DOCUMENT" d ON d.main_body_table_id = "ZWDCB_GCYF_TB" AND (d.main_body_guid LIKE p.MZGUID || "%" OR d.adjunct_path LIKE "%" || p.MZGUID || "%") WHERE p.YD_ID = :ydId GROUP BY p.YD_ID, p.YF_ID, p.ZW_MC, p.MZGUID HAVING COUNT(d.PK_UID) < 1', ["ZWDCB_GCYF_TB", "FS_DOCUMENT"]),
        RuleSpec(66, "ADVISORY", "ZWDCB_GCYF_TB", "观测优势植物盖度占比低于 60%", "观测样方优势植物盖度之和小于观测样方总盖度的 60% 时提示核对。", ["YD_ID", "YF_ID", "YOUSHIZHONG", "FVC"], ["YD_ID", "YF_ID"], 'SELECT s.YD_ID, s.YF_ID, SUM(CASE WHEN p.YOUSHIZHONG = "1" THEN CAST(p.FVC AS REAL) ELSE 0 END) AS "优势植物盖度和", s.ZGD AS "观测样方总盖度" FROM "YF_TRCYGC_TB" s JOIN "ZWDCB_GCYF_TB" p ON p.XB_GLH = s.MZGUID WHERE s.YD_ID = :ydId GROUP BY s.YD_ID, s.YF_ID, s.ZGD HAVING s.ZGD IS NOT NULL AND SUM(CASE WHEN p.YOUSHIZHONG = "1" THEN CAST(p.FVC AS REAL) ELSE 0 END) < CAST(s.ZGD AS REAL) * 0.6', ["YF_TRCYGC_TB", "ZWDCB_GCYF_TB"]),
        RuleSpec(67, "MANDATORY", "YF_TRCY_TB", "高大草灌样方面积不是 100 或 25", "高大草灌样方面积应为 100 或 25。", ["YD_ID", "YF_ID", "MIAN_JI"], ["YD_ID", "YF_ID"], select("YF_TRCY_TB", sample_columns([label("MIAN_JI", "面积")]), scoped_main("MIAN_JI IS NULL OR CAST(MIAN_JI AS REAL) NOT IN (100, 25)"))),
        RuleSpec(68, "MANDATORY", "YF_TRCY_TB", "高大草灌样方缺少植物调查表", "1 个高大草灌样方下至少 1 个植物调查表。", ["YD_ID", "YF_ID", "MZGUID"], ["YD_ID", "YF_ID"], related_plant_count_sql("YF_TRCY_TB", "ZWDCB_GDCG_TB", "高大草灌植物调查数量", "COUNT(p.PK_UID) < 1"), ["YF_TRCY_TB", "ZWDCB_GDCG_TB"]),
        RuleSpec(69, "ADVISORY", "ZWDCB_GDCG_TB", "高大草灌株丛数大于 50", "高大草灌植物调查株丛数大于 50 时，请核实是否有误。", ["YD_ID", "YF_ID", "MC", "ZS"], ["YD_ID", "YF_ID", "MC"], select("ZWDCB_GDCG_TB", plant_columns("MC", [label("ZS", "株丛数")]), scoped_main("ZS IS NOT NULL AND CAST(ZS AS REAL) > 50"))),
        RuleSpec(
            70,
            "ADVISORY",
            "YF_TRCYCC_TB",
            "测产样方草群平均高偏离主要推荐高度",
            "主要推荐高度按覆盖度百分比加权计算：SUM(FVC / 100 * H)，偏差超过 0.5 厘米时提示核对。",
            ["YD_ID", "YF_ID", "CQPJ_GD", "MZGUID"],
            ["YD_ID", "YF_ID"],
            weighted_recommended_height_sql("YF_TRCYCC_TB", "ZWDCB_CCYF_TB"),
            ["YF_TRCYCC_TB", "ZWDCB_CCYF_TB"],
        ),
        RuleSpec(
            71,
            "ADVISORY",
            "YF_TRCYGC_TB",
            "观测样方草群平均高偏离主要推荐高度",
            "主要推荐高度按覆盖度百分比加权计算：SUM(FVC / 100 * H)，偏差超过 0.5 厘米时提示核对。",
            ["YD_ID", "YF_ID", "CQPJ_GD", "MZGUID"],
            ["YD_ID", "YF_ID"],
            weighted_recommended_height_sql("YF_TRCYGC_TB", "ZWDCB_GCYF_TB"),
            ["YF_TRCYGC_TB", "ZWDCB_GCYF_TB"],
        )
    ]


def rule_to_json(spec: RuleSpec) -> dict[str, Any]:
    required_tables = spec.required_tables or [spec.table]
    if spec.table not in required_tables:
        required_tables = [spec.table, *required_tables]
    return {
        "id": f"ADD_GRASS_{spec.source_number:03d}",
        "sourceId": SOURCE_ID,
        "severity": spec.severity,
        "targetTable": spec.table,
        "title": spec.title,
        "explanation": spec.explanation,
        "requiredTables": unique(required_tables),
        "requiredFields": unique(spec.fields),
        "locatorFields": unique(spec.locator_fields),
        "sql": spec.sql,
    }


def unique(values: list[str]) -> list[str]:
    result: list[str] = []
    for value in values:
        if value not in result:
            result.append(value)
    return result


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("rule_set", type=Path, help="Existing baseline rule-set JSON")
    return parser.parse_args()


def main() -> None:
    args = parse_arguments()
    content = json.loads(args.rule_set.read_text(encoding="utf-8"))
    rules = [rule_to_json(spec) for spec in fixture_rules()]
    if len(rules) != 71:
        raise RuntimeError(f"Expected 71 converted rules, got {len(rules)}.")

    content["ruleSetVersion"] = "2026.05-full-grassland"
    content["sources"] = [source for source in content["sources"] if source["id"] != SOURCE_ID]
    content["sources"].append(
        {
            "id": SOURCE_ID,
            "kind": "ADDITIONAL",
            "label": "草地质检附加规则",
            "description": "从草地样地调查质检规则表逐行 SQL 化的附加规则。",
        }
    )
    content["rules"] = [embedded_rule for embedded_rule in content["rules"] if embedded_rule["sourceId"] != SOURCE_ID]
    content["rules"].extend(rules)
    args.rule_set.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Embedded {len(rules)} additional rules into {args.rule_set}.")


if __name__ == "__main__":
    main()
