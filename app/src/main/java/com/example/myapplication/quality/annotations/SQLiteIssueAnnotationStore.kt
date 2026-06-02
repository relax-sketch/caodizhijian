package com.example.myapplication.quality.annotations

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SQLiteIssueAnnotationStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION),
    IssueAnnotationStore {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE ignored_issue (
                fingerprint TEXT PRIMARY KEY NOT NULL,
                ignored_at_epoch_millis INTEGER NOT NULL,
                ignore_reason TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE ignored_issue ADD COLUMN ignore_reason TEXT NOT NULL DEFAULT ''")
        }
    }

    override fun ignoredReasons(fingerprints: Set<String>): Map<String, String> {
        if (fingerprints.isEmpty()) return emptyMap()
        val placeholders = fingerprints.joinToString(",") { "?" }
        return readableDatabase.query(
            "ignored_issue",
            arrayOf("fingerprint", "ignore_reason"),
            "fingerprint IN ($placeholders)",
            fingerprints.toTypedArray(),
            null,
            null,
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), cursor.getString(1).orEmpty())
                }
            }
        }
    }

    override fun markIgnored(fingerprint: String, reason: String, ignoredAtEpochMillis: Long) {
        writableDatabase.insertWithOnConflict(
            "ignored_issue",
            null,
            ContentValues().apply {
                put("fingerprint", fingerprint)
                put("ignored_at_epoch_millis", ignoredAtEpochMillis)
                put("ignore_reason", reason)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun removeIgnored(fingerprint: String) {
        writableDatabase.delete("ignored_issue", "fingerprint = ?", arrayOf(fingerprint))
    }

    private companion object {
        const val DATABASE_NAME = "quality_annotations.db"
        const val DATABASE_VERSION = 2
    }
}
