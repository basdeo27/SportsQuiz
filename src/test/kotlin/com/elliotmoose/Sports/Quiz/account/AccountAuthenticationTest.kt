package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.account.repository.LocalAuthSessionRepository
import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.repository.LocalAccountRepository
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AccountAuthenticationTest {
    private class TestClock(var now: Instant = Instant.parse("2026-05-01T00:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
    }

    @Test
    fun `tokens require a valid bearer and expire after 24 hours`() {
        val repository = LocalAccountRepository()
        val account = repository.save(Account(username = "name", passwordHash = "hash", nickname = "Player"))
        val clock = TestClock()
        val authentication = AccountAuthentication(repository, clock, SecureRandom(), LocalAuthSessionRepository(), AuthenticationProperties(requireEmailVerification = false))
        val token = authentication.issue(account.id)

        assertThat(authentication.resolve(null)).isNull()
        assertThat(authentication.resolve("Bearer $token")!!.id).isEqualTo(account.id)
        assertThatThrownBy { authentication.resolve("Bearer wrong") }.isInstanceOf(AuthenticationRequiredException::class.java)
        assertThatThrownBy { authentication.resolve(token) }.isInstanceOf(AuthenticationRequiredException::class.java)
        clock.now = clock.now.plusSeconds(24 * 60 * 60)
        assertThatThrownBy { authentication.resolve("Bearer $token") }.isInstanceOf(AuthenticationRequiredException::class.java)
    }

    @Test
    fun `revocation and account deletion invalidate tokens`() {
        val repository = LocalAccountRepository()
        val account = repository.save(Account(username = "name", passwordHash = "hash", nickname = "Player"))
        val authentication = AccountAuthentication(repository, TestClock(), SecureRandom(), LocalAuthSessionRepository(), AuthenticationProperties(requireEmailVerification = false))
        val revoked = authentication.issue(account.id)
        val deleted = authentication.issue(account.id)

        authentication.revoke("Bearer $revoked")
        assertThatThrownBy { authentication.resolve("Bearer $revoked") }.isInstanceOf(AuthenticationRequiredException::class.java)
        repository.delete(account.id)
        assertThatThrownBy { authentication.resolve("Bearer $deleted") }.isInstanceOf(AuthenticationRequiredException::class.java)
    }
    @Test
    fun `sessions survive recreating authentication service and password changes invalidate them`() {
        val accounts = LocalAccountRepository()
        val sessions = LocalAuthSessionRepository()
        val account = accounts.save(Account(username = "user", passwordHash = "first-hash", nickname = "Member", emailVerified = true))
        val properties = AuthenticationProperties()
        val clock = TestClock()
        val first = AccountAuthentication(accounts, clock, SecureRandom(), sessions, properties)
        val token = first.issue(account.id)

        val restarted = AccountAuthentication(accounts, clock, SecureRandom(), sessions, properties)

        assertThat(restarted.resolve("Bearer $token")!!.id).isEqualTo(account.id)
        assertThat(sessions.find(token)).isNull()
        accounts.save(account.copy(passwordHash = "second-hash"))
        assertThatThrownBy { restarted.resolve("Bearer $token") }.isInstanceOf(AuthenticationRequiredException::class.java)
    }

}
