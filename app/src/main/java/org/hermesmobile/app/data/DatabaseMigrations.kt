package org.hermesmobile.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
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
