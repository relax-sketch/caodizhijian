package com.example.myapplication.quality.rules

/**
 * Versioned contract for quality-check rules packaged with the application.
 */
data class EmbeddedRuleSet(
    val schemaVersion: Int,
    val ruleSetVersion: String,
    val publishedAt: String,
    val sources: List<RuleSourceMetadata>,
    val rules: List<EmbeddedRule>,
)

enum class RuleSeverity {
    MANDATORY,
    ADVISORY,
}

enum class RuleSourceKind {
    BASE_SNAPSHOT,
    ADDITIONAL,
}

data class RuleSourceMetadata(
    val id: String,
    val kind: RuleSourceKind,
    val label: String,
    val description: String,
)

data class EmbeddedRule(
    val id: String,
    val sourceId: String,
    val severity: RuleSeverity,
    val targetTable: String,
    val title: String,
    val explanation: String,
    val requiredTables: List<String>,
    val requiredFields: List<String>,
    val locatorFields: List<String>,
    val sql: String,
    val enabled: Boolean = true,
)
