package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.account.repository.*
import com.elliotmoose.Sports.Quiz.common.mail.LocalEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.*

class EmailVerificationServiceTest {
    private class MutableClock(var time: Instant = Instant.parse("2026-05-01T00:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(time, zone)
        override fun instant(): Instant = time
    }
    @Test
    fun `verification expires without accepting the token as an authenticated session`() {
        val accounts = LocalAccountRepository()
        val account = accounts.create(Account(username = "player", email = "player@example.test", passwordHash = "hash", nickname = "Player"))
        val tokens = LocalAuthSessionRepository()
        val inbox = LocalEmailSender()
        val clock = MutableClock()
        val service = EmailVerificationService(accounts, tokens, inbox, AuthenticationProperties(), clock, SecureRandom(), BCryptPasswordEncoder())
        service.send(account.id)
        val token = Regex("verifyEmail=([^\\s]+)").find(inbox.latest(account.email!!)!!.text)!!.groupValues[1]

        clock.time = clock.time.plusSeconds(1800)

        assertThatThrownBy { service.verify(token, "password123") }.isInstanceOf(InvalidAccountRequestException::class.java)
        assertThat(accounts.findById(account.id)!!.emailVerified).isFalse()
    }

    @Test
    fun `development does not send mail or require verification`() {
        val accounts = LocalAccountRepository()
        val account = accounts.create(Account(username = "player", email = "player@example.test", passwordHash = "hash", nickname = "Player"))
        val inbox = LocalEmailSender()
        val service = EmailVerificationService(accounts, LocalAuthSessionRepository(), inbox,
            AuthenticationProperties(requireEmailVerification = false), MutableClock(), SecureRandom(), BCryptPasswordEncoder())

        service.send(account.id)

        assertThat(inbox.latest(account.email!!)).isNull()
    }
}
