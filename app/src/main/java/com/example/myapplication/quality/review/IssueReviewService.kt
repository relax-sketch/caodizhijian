package com.example.myapplication.quality.review

import com.example.myapplication.quality.annotations.IssueAnnotationStore
import com.example.myapplication.quality.check.QualityCheckRun
import com.example.myapplication.quality.domain.CheckIssue

class IssueReviewService(
    private val annotationStore: IssueAnnotationStore,
    private val classifier: IssueReviewClassifier = IssueReviewClassifier(annotationStore),
) {
    fun review(run: QualityCheckRun): ReviewedCheckRun {
        val results = run.plotResults
            .map(classifier::classify)
            .sortedWith(
                compareByDescending<ReviewedPlotResult> { it.pendingCount }
                    .thenBy { it.plot.displayPlotId },
            )
        return ReviewedCheckRun(
            sourceRun = run,
            plotResults = results,
            summary = classifier.summarize(results),
        )
    }

    fun ignore(issue: CheckIssue, reason: String) {
        annotationStore.markIgnored(issue.fingerprint, reason.trim())
    }

    fun cancelIgnore(issue: CheckIssue) {
        annotationStore.removeIgnored(issue.fingerprint)
    }

    fun refresh(result: ReviewedPlotResult): ReviewedPlotResult =
        classifier.classify(result.toPlotCheckResult())
}
