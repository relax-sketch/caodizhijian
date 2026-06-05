package com.example.myapplication.quality.check

import com.example.myapplication.quality.domain.CheckIssue
import com.example.myapplication.quality.domain.DefaultCheckScopeSelector
import com.example.myapplication.quality.domain.PlotCheckResult
import com.example.myapplication.quality.domain.PlotRef
import com.example.myapplication.quality.domain.PlotTable
import com.example.myapplication.quality.domain.ZdbSourceRef
import com.example.myapplication.quality.rules.EmbeddedRule
import com.example.myapplication.quality.rules.EmbeddedRuleSet
import com.example.myapplication.quality.rules.RuleRepository
import com.example.myapplication.quality.rules.RuleSetSummary
import com.example.myapplication.quality.rules.RuleSeverity
import com.example.myapplication.quality.rules.RuleSourceKind
import com.example.myapplication.quality.rules.RuleSourceMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QualityCheckEngineTest {
    private val selector = DefaultCheckScopeSelector()
    private val plots = listOf(
        plot("content://district/a.zdb", "PLOT_1", "PLOT_1", "江岸区"),
        plot("content://district/b.zdb", "PLOT_1", "PLOT_1-2", "江岸区"),
        plot("content://district/c.zdb", "PLOT_2", "PLOT_2", "洪山区"),
    )
    private val engine = QualityCheckEngine(FakeRuleRepository(), FakePlotChecker())

    @Test
    fun check_singleCountyAndAllScopes_keepEquivalentPerPlotResults() {
        val all = engine.check(selector.all(plots))
        val county = engine.check(selector.county(plots, "江岸区"))
        val single = engine.check(selector.single(plots[1]))

        assertEquals(3, all.plotResults.size)
        assertEquals(2, county.plotResults.size)
        assertEquals(
            all.plotResults.filter { it.plot.countyLabel == "江岸区" }.map(::fingerprints),
            county.plotResults.map(::fingerprints),
        )
        assertEquals(fingerprints(all.plotResults[1]), fingerprints(single.plotResults.single()))
    }

    @Test
    fun check_duplicateRawPlotIds_areSeparatedBySourceAndDisplaySuffix() {
        val results = engine.check(selector.all(plots)).plotResults.take(2)

        assertEquals(listOf("PLOT_1", "PLOT_1-2"), results.map { it.plot.displayPlotId })
        assertEquals(listOf("PLOT_1", "PLOT_1"), results.map { it.plot.rawPlotId })
        assertNotEquals(results[0].plot.source.uri, results[1].plot.source.uri)
        assertNotEquals(fingerprints(results[0]), fingerprints(results[1]))
    }

    @Test
    fun check_filtersTemporarilyDisabledRulesBeforeExecution() {
        val checker = RecordingPlotChecker()
        val run = QualityCheckEngine(
            FakeRuleRepository(
                rules = listOf(
                    rule(id = "ACTIVE", targetTable = "YD_TRCY_PT"),
                    rule(id = "DISABLED", targetTable = "YD_JM_PT"),
                    rule(id = "DISABLED_BY_FLAG", targetTable = "YD_TRCY_PT", enabled = false),
                ),
            ),
            checker,
        ).check(selector.single(plots.first()))

        assertEquals(listOf("ACTIVE"), checker.receivedRuleIds)
        assertEquals(1, run.plotResults.single().executedRuleCount)
    }

    @Test
    fun check_canExcludeBaselineSnapshotRules() {
        val checker = RecordingPlotChecker()
        QualityCheckEngine(
            FakeRuleRepository(
                sources = listOf(
                    RuleSourceMetadata("base", RuleSourceKind.BASE_SNAPSHOT, "base", "base"),
                    RuleSourceMetadata("additional", RuleSourceKind.ADDITIONAL, "additional", "additional"),
                ),
                rules = listOf(
                    rule(id = "BASE_RULE", sourceId = "base"),
                    rule(id = "ADDITIONAL_RULE", sourceId = "additional"),
                ),
            ),
            checker,
        ).check(
            scope = selector.single(plots.first()),
            includeBaselineRules = false,
        )

        assertEquals(listOf("ADDITIONAL_RULE"), checker.receivedRuleIds)
    }

    private fun fingerprints(result: PlotCheckResult): List<String> = result.issues.map(CheckIssue::fingerprint)

    private fun plot(uri: String, rawId: String, displayId: String, county: String): PlotRef =
        PlotRef(
            source = ZdbSourceRef(uri, county, "$displayId.zdb", 1),
            rawPlotId = rawId,
            displayPlotId = displayId,
            countyCode = county,
            countyLabel = county,
            plotTable = PlotTable.NATURAL_GRASSLAND,
            parentGuid = null,
        )

    private class FakePlotChecker : PlotChecker {
        override fun check(plot: PlotRef, rules: List<EmbeddedRule>): PlotCheckResult =
            PlotCheckResult(
                plot = plot,
                issues = listOf(
                    CheckIssue(
                        fingerprint = "${plot.source.uri}|${plot.rawPlotId}|${rules.single().id}",
                        plot = plot,
                        ruleId = rules.single().id,
                        severity = RuleSeverity.MANDATORY,
                        title = "fixture issue",
                        explanation = "fixture issue",
                        tableName = "YD_TRCY_PT",
                        locationValues = mapOf("YD_ID" to plot.rawPlotId),
                        actualValues = emptyMap(),
                    ),
                ),
                skippedRules = emptyList(),
                passedRules = emptyList(),
                executedRuleCount = 1,
            )
    }

    private fun rule(
        id: String = "TEST",
        targetTable: String = "YD_TRCY_PT",
        sourceId: String = "test",
        enabled: Boolean = true,
    ): EmbeddedRule =
        EmbeddedRule(
            id = id,
            sourceId = sourceId,
            severity = RuleSeverity.MANDATORY,
            targetTable = targetTable,
            title = "test",
            explanation = "test",
            requiredTables = listOf(targetTable),
            requiredFields = listOf("YD_ID"),
            locatorFields = listOf("YD_ID"),
            sql = "SELECT YD_ID FROM $targetTable WHERE YD_ID = :ydId",
            enabled = enabled,
        )

    private class RecordingPlotChecker : PlotChecker {
        var receivedRuleIds: List<String> = emptyList()

        override fun check(plot: PlotRef, rules: List<EmbeddedRule>): PlotCheckResult {
            receivedRuleIds = rules.map(EmbeddedRule::id)
            return PlotCheckResult(
                plot = plot,
                issues = emptyList(),
                skippedRules = emptyList(),
                passedRules = emptyList(),
                executedRuleCount = rules.size,
            )
        }
    }

    private class FakeRuleRepository(
        private val rules: List<EmbeddedRule> = listOf(defaultRule()),
        private val sources: List<RuleSourceMetadata> =
            listOf(RuleSourceMetadata("test", RuleSourceKind.ADDITIONAL, "test", "test")),
    ) : RuleRepository {
        override fun loadRuleSet(): EmbeddedRuleSet =
            EmbeddedRuleSet(
                schemaVersion = 1,
                ruleSetVersion = "test",
                publishedAt = "2026-05-26",
                sources = sources,
                rules = rules,
            )

        override fun summary(): RuleSetSummary =
            RuleSetSummary("test", 1, 0, 1, 1, 0)

        private companion object {
            fun defaultRule(): EmbeddedRule =
                EmbeddedRule(
                    id = "TEST",
                    sourceId = "test",
                    severity = RuleSeverity.MANDATORY,
                    targetTable = "YD_TRCY_PT",
                    title = "test",
                    explanation = "test",
                    requiredTables = listOf("YD_TRCY_PT"),
                    requiredFields = listOf("YD_ID"),
                    locatorFields = listOf("YD_ID"),
                    sql = "SELECT YD_ID FROM YD_TRCY_PT WHERE YD_ID = :ydId",
                )
        }
    }
}
