package org.hermesmobile.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TodayDao {
    @Query("SELECT COUNT(*) FROM phone_captures WHERE processedAt IS NULL")
    fun unprocessedCaptures(): Flow<Int>

    @Query("""SELECT session.scope, session.key AS sessionKey, session.title AS sessionTitle,
        control.runId, control.requestId, control.`key` AS controlKey, control.generation,
        control.receivedAt AS lastConfirmedAt
        FROM pending_controls control INNER JOIN sessions session ON session.key = control.sessionKey
        WHERE session.scope = :scope AND session.hidden = 0 AND session.runId = control.runId
            AND session.executionGeneration IS control.generation AND session.status NOT IN (:terminal)
            AND control.kind = 'approval' AND control.state = 'pending'
        ORDER BY control.receivedAt DESC, control.key LIMIT 6""")
    fun pendingApprovals(scope: String, terminal: List<String>): Flow<List<TodayApproval>>

    @Query("""SELECT session.scope, session.key AS sessionKey, session.title AS sessionTitle,
        report.runId, report.key AS receiptKey, report.updatedAt AS lastConfirmedAt
        FROM run_reports report INNER JOIN sessions session ON session.key = report.sessionKey
        WHERE session.scope = :scope AND session.hidden = 0 AND report.status = 'completed'
            AND report.transport = 'runs' AND report.runId != ''
        UNION ALL
        SELECT session.scope, session.key AS sessionKey, session.title AS sessionTitle,
            session.runId, session.key || ':' || session.runId AS receiptKey, 0 AS lastConfirmedAt
        FROM sessions session
        WHERE session.scope = :scope AND session.hidden = 0 AND session.transport = 'runs'
            AND session.runId != '' AND session.status = 'completed'
            AND NOT EXISTS (SELECT 1 FROM run_reports report WHERE report.sessionKey = session.key
                AND report.runId = session.runId AND report.status = 'completed' AND report.transport = 'runs')
        ORDER BY lastConfirmedAt DESC, receiptKey LIMIT 6""")
    fun completedRuns(scope: String): Flow<List<TodayRun>>

    @Query("SELECT * FROM sessions WHERE scope = :scope AND `key` = :sessionKey AND hidden = 0")
    suspend fun visibleSession(scope: String, sessionKey: String): Conversation?

    @Query("SELECT * FROM run_reports WHERE `key` = :receiptKey")
    suspend fun receipt(receiptKey: String): RunReport?

    @Query("""SELECT * FROM tool_events WHERE sessionKey = :sessionKey AND runId = :runId
        ORDER BY receivedAt DESC, `key` LIMIT 200""")
    suspend fun savedRunEvents(sessionKey: String, runId: String): List<ToolEvent>

    @Transaction
    suspend fun completedRun(row: TodayRun): TodayRunDetails? {
        val session = visibleSession(row.scope, row.sessionKey) ?: return null
        val report = receipt(row.receiptKey)
        if (report != null) {
            if (report.sessionKey != row.sessionKey || report.runId != row.runId ||
                report.status != "completed" || report.transport != "runs") return null
        } else {
            // Older app versions may have only the latest completed session receipt.
            if (row.receiptKey != session.key + ":" + row.runId || session.runId != row.runId ||
                session.status != "completed" || session.transport != "runs") return null
        }
        return TodayRunDetails(report, savedRunEvents(row.sessionKey, row.runId))
    }

    @Query("""SELECT session.* FROM sessions session
        WHERE session.scope = :scope AND session.hidden = 0 AND session.transport = 'runs' AND session.runId != ''
        ORDER BY CASE WHEN session.status IN (:terminal) THEN 1 ELSE 0 END, session.updatedAt DESC, session.key
        LIMIT 8""")
    suspend fun refreshCandidates(scope: String, terminal: List<String>): List<Conversation>
}
