package com.example.myapplication.quality.rules

object TemporarilyDisabledRules {
    private val encryptedSampleTables = setOf(
        "YD_JM_PT",
        "YF_JMCC_TB",
        "ZWDCB_JMCCYF_TB",
        "YF_JMGDCG_TB",
        "ZWDCB_JMGDCG_TB",
    )

    fun isDisabled(rule: EmbeddedRule): Boolean {
        val targetTable = rule.targetTable.uppercase()
        val requiredTables = rule.requiredTables.map(String::uppercase)
        val sql = rule.sql.uppercase()
        return !rule.enabled ||
            targetTable in encryptedSampleTables ||
            requiredTables.any { it in encryptedSampleTables } ||
            encryptedSampleTables.any { table -> table in sql }
    }

    fun enabledRules(rules: List<EmbeddedRule>): List<EmbeddedRule> =
        rules.filterNot(::isDisabled)
}
