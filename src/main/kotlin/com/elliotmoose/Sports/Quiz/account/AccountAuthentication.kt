package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import com.elliotmoose.Sports.Quiz.account.repository.AuthSession
import com.elliotmoose.Sports.Quiz.account.repository.AuthSessionRepository
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentityProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Clock
import java.util.Base64

@Service
@EnableConfigurationProperties(AuthenticationProperties::class)
class AccountAuthentication(
    private val repository: AccountRepository,
    private val clock: Clock,
    private val random: SecureRandom,
    private val sessions: AuthSessionRepository,
    private val properties: AuthenticationProperties
) : UserIdentityProvider {
    fun issue(accountId: String): String {
        val account = repository.findById(accountId) ?: throw AuthenticationRequiredException()
        if (properties.requireEmailVerification && !account.emailVerified) throw EmailVerificationRequiredException()
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        // Only a verifier is persisted. A read of the session table cannot recover bearer credentials.
        sessions.save(AuthSession(hash(token), accountId, hash(account.passwordHash),
            clock.millis() + Duration.ofHours(properties.sessionHours).toMillis()))
        return token
    }

    override fun resolve(authorization: String?): UserIdentity? {
        if (authorization == null) return null
        val token = authorization.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
            ?: throw AuthenticationRequiredException()
        val session = sessions.find(hash(token)) ?: throw AuthenticationRequiredException()
        val account = repository.findById(session.accountId)
        if (session.purpose != "SESSION") throw AuthenticationRequiredException()
        if (session.expiresAt <= clock.millis() || account == null ||
            (properties.requireEmailVerification && !account.emailVerified) ||
            !MessageDigest.isEqual(session.credentialFingerprint.toByteArray(), hash(account.passwordHash).toByteArray())) {
            sessions.delete(session.id)
            throw AuthenticationRequiredException()
        }
        return UserIdentity(account.id, account.nickname)
    }

    fun revoke(authorization: String?) {
        authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.let { sessions.delete(hash(it)) }
    }

    private fun hash(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}
