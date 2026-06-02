package com.example.myapplication.quality.domain

import com.example.myapplication.quality.rules.EmbeddedRule
import com.example.myapplication.quality.rules.RuleSeverity

data class SkippedRule(
    val plot: PlotRef,
    val ruleId: String,
    val severity: RuleSeverity,
    val title: String,
    val tableName: String,
    val reason: String,
)

data class CheckIssue(
    val fingerprint: String,
    val plot: PlotRef,
    val ruleId: String,
    val severity: RuleSeverity,
    val title: String,
    val explanation: String,
    val tableName: String,
    val locationValues: Map<String, String?>,
    val actualValues: Map<String, String?>,
    val ignored: Boolean = false,
    val ignoredReason: String? = null,
)

data class PassedRule(
    val plot: PlotRef,
    val ruleId: String,
    val severity: RuleSeverity,
    val title: String,
    val explanation: String,
    val tableName: String,
)

data class PlotCheckResult(
    val plot: PlotRef,
    val issues: List<CheckIssue>,
    val skippedRules: List<SkippedRule>,
    val passedRules: List<PassedRule>,
    val executedRuleCount: Int,
)

data class PreparedRuleQuery(
    val rule: EmbeddedRule,
    val sql: String,
    val arguments: Array<String>,
)
