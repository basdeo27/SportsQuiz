package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import com.elliotmoose.Sports.Quiz.account.repository.AuthSession
import com.elliotmoose.Sports.Quiz.account.repository.AuthSessionRepository
import com.elliotmoose.Sports.Quiz.common.mail.EmailMessage
import com.elliotmoose.Sports.Quiz.common.mail.EmailSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

@Service
class EmailVerificationService(
    private val accounts: AccountRepository,
    private val tokens: AuthSessionRepository,
    private val sender: EmailSender,
    private val properties: AuthenticationProperties,
    private val clock: Clock,
    private val random: SecureRandom,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun send(accountId: String) {
        val account = accounts.findById(accountId) ?: return
        if (account.emailVerified || !properties.requireEmailVerification) return
        val email = account.email ?: return
        val cooldownId = "MAIL#${hash(email)}"
        if (!tokens.saveIfExpired(AuthSession(cooldownId, account.id, "", clock.millis() + 60000, "MAIL_COOLDOWN"), clock.millis())) return
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val id = "VERIFY#${hash(token)}"
        tokens.save(AuthSession(id, account.id, hash(email),
            clock.millis() + Duration.ofMinutes(properties.verificationMinutes).toMillis(), "VERIFY_EMAIL"))
        try {
            sender.send(EmailMessage(email, "Verify your Sports Quiz email",
                "Welcome to Sports Quiz. Open this link and confirm your password to verify your email:\n\n${properties.frontendUrl.trimEnd('/')}?verifyEmail=$token\n\nThis link expires in ${properties.verificationMinutes} minutes. If you did not request it, ignore this email."))
        } catch (ex: Exception) {
            tokens.delete(id)
            tokens.delete(cooldownId)
            logger.error("Verification delivery failed for accountId={}", account.id)
            throw VerificationDeliveryException()
        }
    }

    fun resend(email: String) {
        accounts.findByEmail(email.trim().lowercase())?.let { send(it.id) }
    }

    fun verify(token: String, password: String): Account {
        if (token.length !in 32..256) throw InvalidAccountRequestException("Verification link is invalid or expired. Request a new email.")
        val id = "VERIFY#${hash(token)}"
        val record = tokens.find(id) ?: throw InvalidAccountRequestException("Verification link is invalid or expired. Request a new email.")
        if (record.purpose != "VERIFY_EMAIL" || record.expiresAt <= clock.millis()) {
            tokens.delete(id)
            throw InvalidAccountRequestException("Verification link is invalid or expired. Request a new email.")
        }
        val account = accounts.findById(record.accountId) ?: throw InvalidAccountRequestException("Account no longer exists.")
        if (account.email == null || record.credentialFingerprint != hash(account.email)) throw InvalidAccountRequestException("Verification link no longer matches this email.")
        if (password.toByteArray(Charsets.UTF_8).size > 72 || !passwordEncoder.matches(password, account.passwordHash)) throw InvalidCredentialsException()
        // Persist verification first. A delivery retry can complete a previously interrupted verification.
        val verified = accounts.save(account.copy(emailVerified = true))
        if (tokens.consume(id) == null) throw InvalidAccountRequestException("This verification link has already been used.")
        return verified
    }

    private fun hash(value: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))
}
