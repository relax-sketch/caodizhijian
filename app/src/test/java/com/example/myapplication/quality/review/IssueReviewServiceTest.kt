package com.example.myapplication.quality.review

import com.example.myapplication.quality.annotations.IssueAnnotationStore
import com.example.myapplication.quality.annotations.IssueAnnotation
import com.example.myapplication.quality.check.QualityCheckRun
import com.example.myapplication.quality.domain.CheckIssue
import com.example.myapplication.quality.domain.CheckScope
import com.example.myapplication.quality.domain.PassedRule
import com.example.myapplication.quality.domain.PlotCheckResult
import com.example.myapplication.quality.domain.PlotRef
import com.example.myapplication.quality.domain.PlotTable
import com.example.myapplication.quality.domain.SkippedRule
import com.example.myapplication.quality.domain.ZdbSourceRef
import com.example.myapplication.quality.rules.RuleSetSummary
import com.example.myapplication.quality.rules.RuleSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class IssueReviewServiceTest {
    private val plot = PlotRef(
        source = ZdbSourceRef("content://fixture", "fixture", "fixture.zdb", 1),
        rawPlotId = "PLOT_1",
        displayPlotId = "PLOT_1",
        countyCode = "001",
        countyLabel = "江岸区",
        plotTable = PlotTable.NATURAL_GRASSLAND,
        parentGuid = null,
    )
    private val issue = CheckIssue(
        fingerprint = "fingerprint-1",
        plot = plot,
        ruleId = "RULE_1",
        severity = RuleSeverity.MANDATORY,
        title = "问题",
        explanation = "说明",
        tableName = "YD_TRCY_PT",
        locationValues = mapOf("YD_ID" to "PLOT_1"),
        actualValues = mapOf("actualValue" to "bad"),
    )
    private val advisoryIssue = issue.copy(
        fingerprint = "fingerprint-2",
        ruleId = "RULE_2",
        severity = RuleSeverity.ADVISORY,
        title = "提示问题",
    )
    private val skippedRule = SkippedRule(
        plot = plot,
        ruleId = "RULE_SKIP",
        severity = RuleSeverity.MANDATORY,
        title = "跳过规则",
        tableName = "YD_TRCY_PT",
        reason = "missing table",
    )
    private val passedRule = PassedRule(
        plot = plot,
        ruleId = "RULE_PASS",
        severity = RuleSeverity.ADVISORY,
        title = "通过规则",
        explanation = "说明",
        tableName = "YD_TRCY_PT",
    )

    @Test
    fun ignoreAndCancelIgnore_moveIssueBetweenPendingAndIgnoredSections() {
        val service = IssueReviewService(InMemoryAnnotationStore())
        val run = runWithIssues(listOf(issue))

        assertEquals(1, service.review(run).plotResults.single().pendingMandatory.size)
        service.ignore(issue, "field verified manually")
        assertEquals(1, service.review(run).plotResults.single().ignored.size)
        service.cancelIgnore(issue)
        assertEquals(1, service.review(run).plotResults.single().pendingMandatory.size)
    }

    @Test
    fun recheck_preservesStillMatchingIgnoreAndRemovesResolvedIssue() {
        val service = IssueReviewService(InMemoryAnnotationStore())
        service.ignore(issue, "field verified manually")

        assertEquals(1, service.review(runWithIssues(listOf(issue))).plotResults.single().ignored.size)
        val resolvedReview = service.review(runWithIssues(emptyList()))
        assertEquals(0, resolvedReview.plotResults.single().ignored.size)
        assertEquals(0, resolvedReview.summary.pendingMandatoryIssues)
        assertEquals(0, resolvedReview.summary.ignoredIssues)
    }

    @Test
    fun review_preservesPassedRulesForTestModeDisplay() {
        val reviewed = IssueReviewService(InMemoryAnnotationStore())
            .review(runWithIssues(issues = emptyList(), passedRules = listOf(passedRule)))

        assertEquals(listOf(passedRule), reviewed.plotResults.single().passedRules)
        assertEquals(1, reviewed.summary.passedRules)
        assertEquals(1, reviewed.summary.executedRules)
    }

    @Test
    fun reviewedPlotResult_detailCountTextIncludesPendingSkippedAndIgnoredCounts() {
        val service = IssueReviewService(InMemoryAnnotationStore())
        service.ignore(issue, "field verified manually")

        val reviewed = service.review(
            runWithIssues(
                issues = listOf(issue, advisoryIssue),
                skippedRules = listOf(skippedRule),
            ),
        ).plotResults.single()

        assertEquals("强制性 0 · 提示性 1 · 跳过 1 · 忽略 1", reviewed.detailCountText)
    }

    @Test
    fun review_includesIgnoreReasonOnIgnoredIssue() {
        val service = IssueReviewService(InMemoryAnnotationStore())
        service.ignore(issue, "checked against source document")

        val ignoredIssue = service.review(runWithIssues(listOf(issue))).plotResults.single().ignored.single()

        assertEquals("checked against source document", ignoredIssue.ignoredReason)
    }

    @Test
    fun cancelIgnore_keepsReasonAvailableForLaterPrefill() {
        val store = InMemoryAnnotationStore()
        val service = IssueReviewService(store)
        service.ignore(issue, "checked against source document")
        service.cancelIgnore(issue)

        val reviewedIssue = service.review(runWithIssues(listOf(issue))).plotResults.single().pendingMandatory.single()

        assertEquals("checked against source document", reviewedIssue.ignoredReason)
        assertEquals(false, store.annotations(setOf(issue.fingerprint)).getValue(issue.fingerprint).ignored)
    }

    private fun runWithIssues(
        issues: List<CheckIssue>,
        skippedRules: List<SkippedRule> = emptyList(),
        passedRules: List<PassedRule> = emptyList(),
    ): QualityCheckRun =
        QualityCheckRun(
            scope = CheckScope.Single(plot),
            ruleSetSummary = RuleSetSummary("test", 1, 0, 1, 1, 0),
            plotResults = listOf(
                PlotCheckResult(
                    plot = plot,
                    issues = issues,
                    skippedRules = skippedRules,
                    passedRules = passedRules,
                    executedRuleCount = 1,
                ),
            ),
            cancelled = false,
        )

    private class InMemoryAnnotationStore : IssueAnnotationStore {
        private val ignored = mutableMapOf<String, IssueAnnotation>()

        override fun annotations(fingerprints: Set<String>): Map<String, IssueAnnotation> =
            ignored.filterKeys { it in fingerprints }

        override fun markIgnored(fingerprint: String, reason: String, ignoredAtEpochMillis: Long) {
            ignored[fingerprint] = IssueAnnotation(ignored = true, reason = reason)
        }

        override fun removeIgnored(fingerprint: String) {
            ignored[fingerprint]?.let { annotation ->
                ignored[fingerprint] = annotation.copy(ignored = false)
            }
        }
    }
}
