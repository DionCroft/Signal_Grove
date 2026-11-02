package org.hermesmobile.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val FROM_4_TO_5=object:Migration(4,5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN channelKey TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sessions ADD COLUMN pendingRequestHash TEXT NOT NULL DEFAULT ''")
        }
    }

    val FROM_3_TO_4=object:Migration(3,4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS job_operations (`key` TEXT NOT NULL, scope TEXT NOT NULL, operation TEXT NOT NULL, jobId TEXT NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_job_operations_scope ON job_operations(scope)")
            db.execSQL("CREATE TABLE IF NOT EXISTS notification_receipts (`key` TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
        }
    }

    val FROM_2_TO_3 = object : Migration(2,3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN lastEventSeq INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE sessions ADD COLUMN admissionId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sessions ADD COLUMN executionGeneration INTEGER DEFAULT NULL")
            db.execSQL("CREATE TABLE IF NOT EXISTS tool_events (`key` TEXT NOT NULL, sessionKey TEXT NOT NULL, runId TEXT NOT NULL, tool TEXT NOT NULL, kind TEXT NOT NULL, preview TEXT NOT NULL, durationMs INTEGER, receivedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tool_events_sessionKey ON tool_events(sessionKey)")
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_controls (`key` TEXT NOT NULL, sessionKey TEXT NOT NULL, runId TEXT NOT NULL, requestId TEXT NOT NULL, generation INTEGER, kind TEXT NOT NULL, prompt TEXT NOT NULL, choicesJson TEXT NOT NULL, limited INTEGER NOT NULL, state TEXT NOT NULL, receivedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_controls_sessionKey ON pending_controls(sessionKey)")
        }
    }
    val FROM_1_TO_2 = object : Migration(1,2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sessions ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE IF NOT EXISTS cached_jobs (`key` TEXT NOT NULL, scope TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, prompt TEXT NOT NULL, schedule TEXT NOT NULL, state TEXT NOT NULL, enabled INTEGER NOT NULL, nextRun TEXT NOT NULL, lastRun TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_jobs_scope ON cached_jobs(scope)")
        }
    }
}

val ALL_HERMES_MIGRATIONS = arrayOf(DatabaseMigrations.FROM_1_TO_2,DatabaseMigrations.FROM_2_TO_3,DatabaseMigrations.FROM_3_TO_4,DatabaseMigrations.FROM_4_TO_5)
