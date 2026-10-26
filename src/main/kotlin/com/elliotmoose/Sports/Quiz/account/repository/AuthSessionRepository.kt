package com.elliotmoose.Sports.Quiz.account.repository

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

data class AuthSession(val id: String, val accountId: String, val credentialFingerprint: String, val expiresAt: Long, val purpose: String = "SESSION")

interface AuthSessionRepository {
    fun saveIfExpired(session: AuthSession, now: Long): Boolean
    fun save(session: AuthSession)
    fun find(id: String): AuthSession?
    fun delete(id: String)
    fun consume(id: String): AuthSession?
}

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalAuthSessionRepository : AuthSessionRepository {
    private val sessions = ConcurrentHashMap<String, AuthSession>()
    override fun saveIfExpired(session: AuthSession, now: Long): Boolean {
        var saved = false
        sessions.compute(session.id) { _, previous ->
            if (previous == null || previous.expiresAt <= now) { saved = true; session } else previous
        }
        return saved
    }
    override fun save(session: AuthSession) { sessions[session.id] = session }
    override fun find(id: String): AuthSession? = sessions[id]
    override fun delete(id: String) { sessions.remove(id) }
    override fun consume(id: String): AuthSession? = sessions.remove(id)
}
