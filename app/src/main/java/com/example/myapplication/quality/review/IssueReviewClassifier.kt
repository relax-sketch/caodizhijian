package com.example.myapplication.quality.review

import com.example.myapplication.quality.annotations.IssueAnnotationStore
import com.example.myapplication.quality.domain.CheckIssue
import com.example.myapplication.quality.domain.PlotCheckResult
import com.example.myapplication.quality.rules.RuleSeverity

class IssueReviewClassifier(
    private val annotationStore: IssueAnnotationStore,
) {
    fun classify(result: PlotCheckResult): ReviewedPlotResult {
        val annotations = annotationStore.annotations(
            result.issues.map(CheckIssue::fingerprint).toSet(),
        )
        val annotatedIssues = result.issues.map { issue ->
            val annotation = annotations[issue.fingerprint]
            issue.copy(
                ignored = annotation?.ignored == true,
                ignoredReason = annotation?.reason,
            )
        }
        val pendingMandatory = annotatedIssues
            .filter { !it.ignored && it.severity == RuleSeverity.MANDATORY }
            .sortedWith(issueComparator)
        val pendingAdvisory = annotatedIssues
            .filter { !it.ignored && it.severity == RuleSeverity.ADVISORY }
            .sortedWith(issueComparator)
        val ignored = annotatedIssues
            .filter(CheckIssue::ignored)
            .sortedWith(issueComparator)
        return ReviewedPlotResult(
            plot = result.plot,
            pendingMandatory = pendingMandatory,
            pendingAdvisory = pendingAdvisory,
            ignored = ignored,
            skippedRules = result.skippedRules,
            passedRules = result.passedRules.sortedWith(compareBy({ it.title }, { it.ruleId })),
            executedRuleCount = result.executedRuleCount,
        )
    }

    fun summarize(results: List<ReviewedPlotResult>): ReviewSummary =
        ReviewSummary(
            checkedPlots = results.size,
            plotsWithMandatoryIssues = results.count { it.pendingMandatory.isNotEmpty() },
            pendingMandatoryIssues = results.sumOf { it.pendingMandatory.size },
            pendingAdvisoryIssues = results.sumOf { it.pendingAdvisory.size },
            ignoredIssues = results.sumOf { it.ignored.size },
            executedRules = results.sumOf { it.executedRuleCount },
            skippedRules = results.sumOf { it.skippedRules.size },
            passedRules = results.sumOf { it.passedRules.size },
        )

    private companion object {
        val issueComparator = compareBy<CheckIssue>({ it.title }, { it.ruleId }, { it.fingerprint })
    }
}
