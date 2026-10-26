package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.model.DailySession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

interface DailyAttemptRepository {
    fun createIfAbsent(session: DailySession): DailySession
    fun find(id: String): DailySession?
    fun replace(expectedVersion: Long, updated: DailySession): Boolean
}

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalDailyAttemptRepository : DailyAttemptRepository {
    private val attempts = ConcurrentHashMap<String, DailySession>()
    override fun createIfAbsent(session: DailySession): DailySession = attempts.putIfAbsent(session.id, session) ?: session
    override fun find(id: String): DailySession? = attempts[id]
    override fun replace(expectedVersion: Long, updated: DailySession): Boolean {
        var replaced = false
        attempts.computeIfPresent(updated.id) { _, previous ->
            if (previous.version == expectedVersion) { replaced = true; updated } else previous
        }
        return replaced
    }
}
