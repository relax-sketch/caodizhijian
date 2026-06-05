package com.example.myapplication.quality.annotations

data class IssueAnnotation(
    val ignored: Boolean,
    val reason: String,
)

interface IssueAnnotationStore {
    fun annotations(fingerprints: Set<String>): Map<String, IssueAnnotation>

    fun ignoredReasons(fingerprints: Set<String>): Map<String, String> =
        annotations(fingerprints)
            .filterValues(IssueAnnotation::ignored)
            .mapValues { it.value.reason }

    fun markIgnored(
        fingerprint: String,
        reason: String,
        ignoredAtEpochMillis: Long = System.currentTimeMillis(),
    )

    fun removeIgnored(fingerprint: String)
}
