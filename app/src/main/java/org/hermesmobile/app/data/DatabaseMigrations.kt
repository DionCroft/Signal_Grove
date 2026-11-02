package org.hermesmobile.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val FROM_8_TO_9=object:Migration(8,9) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS management_operations (`key` TEXT NOT NULL,resourceScope TEXT NOT NULL,apiScope TEXT NOT NULL,url TEXT NOT NULL,profile TEXT NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,target TEXT NOT NULL,requestSummary TEXT NOT NULL,state TEXT NOT NULL,resultSummary TEXT NOT NULL,createdAt INTEGER NOT NULL,PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_management_operations_resourceScope ON management_operations(resourceScope)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_management_operations_apiScope ON management_operations(apiScope)")
        }
    }

    val FROM_7_TO_8=object:Migration(7,8) {
        override fun migrate(db:SupportSQLiteDatabase) {db.execSQL("ALTER TABLE messages ADD COLUMN imagesJson TEXT NOT NULL DEFAULT ''")}
    }

    val FROM_6_TO_7=object:Migration(6,7) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS run_reports (`key` TEXT NOT NULL,sessionKey TEXT NOT NULL,runId TEXT NOT NULL,pendingKey TEXT NOT NULL,transport TEXT NOT NULL,telemetryJson TEXT NOT NULL,status TEXT NOT NULL,updatedAt INTEGER NOT NULL,PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_run_reports_sessionKey ON run_reports(sessionKey)")
            db.execSQL("ALTER TABLE messages ADD COLUMN telemetryJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tool_events ADD COLUMN callId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tool_events ADD COLUMN category TEXT NOT NULL DEFAULT 'tool'")
            db.execSQL("ALTER TABLE tool_events ADD COLUMN detailsJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tool_events ADD COLUMN generation INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE tool_events ADD COLUMN sequence INTEGER DEFAULT NULL")
        }
    }

    val FROM_5_TO_6=object:Migration(5,6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN detailsJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE TABLE IF NOT EXISTS server_operations (`key` TEXT NOT NULL, scope TEXT NOT NULL, sessionKey TEXT NOT NULL, action TEXT NOT NULL, target TEXT NOT NULL, requestJson TEXT NOT NULL, state TEXT NOT NULL, responseJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_server_operations_scope ON server_operations(scope)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_server_operations_sessionKey ON server_operations(sessionKey)")
        }
    }

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

val ALL_HERMES_MIGRATIONS = arrayOf(DatabaseMigrations.FROM_1_TO_2,DatabaseMigrations.FROM_2_TO_3,DatabaseMigrations.FROM_3_TO_4,DatabaseMigrations.FROM_4_TO_5,DatabaseMigrations.FROM_5_TO_6,DatabaseMigrations.FROM_6_TO_7,DatabaseMigrations.FROM_7_TO_8,DatabaseMigrations.FROM_8_TO_9)
