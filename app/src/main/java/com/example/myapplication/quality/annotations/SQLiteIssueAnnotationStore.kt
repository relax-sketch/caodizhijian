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
                ignore_reason TEXT NOT NULL DEFAULT '',
                is_ignored INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE ignored_issue ADD COLUMN ignore_reason TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            database.execSQL("ALTER TABLE ignored_issue ADD COLUMN is_ignored INTEGER NOT NULL DEFAULT 1")
        }
    }

    override fun annotations(fingerprints: Set<String>): Map<String, IssueAnnotation> {
        if (fingerprints.isEmpty()) return emptyMap()
        val placeholders = fingerprints.joinToString(",") { "?" }
        return readableDatabase.query(
            "ignored_issue",
            arrayOf("fingerprint", "ignore_reason", "is_ignored"),
            "fingerprint IN ($placeholders)",
            fingerprints.toTypedArray(),
            null,
            null,
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    put(
                        cursor.getString(0),
                        IssueAnnotation(
                            ignored = cursor.getInt(2) == 1,
                            reason = cursor.getString(1).orEmpty(),
                        ),
                    )
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
                put("is_ignored", 1)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun removeIgnored(fingerprint: String) {
        writableDatabase.update(
            "ignored_issue",
            ContentValues().apply {
                put("is_ignored", 0)
            },
            "fingerprint = ?",
            arrayOf(fingerprint),
        )
    }

    private companion object {
        const val DATABASE_NAME = "quality_annotations.db"
        const val DATABASE_VERSION = 3
    }
}
