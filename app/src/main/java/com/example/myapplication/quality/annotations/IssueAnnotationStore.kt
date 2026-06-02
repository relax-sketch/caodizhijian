package com.example.myapplication.quality.annotations

interface IssueAnnotationStore {
    fun ignoredReasons(fingerprints: Set<String>): Map<String, String>

    fun markIgnored(
        fingerprint: String,
        reason: String,
        ignoredAtEpochMillis: Long = System.currentTimeMillis(),
    )

    fun removeIgnored(fingerprint: String)
}
