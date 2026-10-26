package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.model.LoginRequest
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import com.elliotmoose.Sports.Quiz.account.repository.LocalAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration

class DevelopmentAccountSeederTest {
    private val repository = LocalAccountRepository()
    private val encoder = BCryptPasswordEncoder()
    private val seeder = DevelopmentAccountSeeder(repository, encoder)
    private val service = AccountService(repository, encoder, AuthenticationProperties(requireEmailVerification = false, allowDevelopmentLogin = true))

    @Test
    fun `default account can log in with user credentials but rejects incorrect password`() {
        seeder.seedAccount()

        val response = service.login(LoginRequest("user", "user"))

        assertThat(response.username).isEqualTo("user")
        assertThat(repository.findByUsername("user")!!.passwordHash).isNotEqualTo("user")
        assertThatThrownBy { service.login(LoginRequest("user", "wrong")) }
            .isInstanceOf(InvalidCredentialsException::class.java)
    }

    @Test
    fun `restarting preserves the existing account and restores its development password`() {
        val existing = repository.save(Account(
            username = "user",
            passwordHash = encoder.encode("changed-password"),
            nickname = "Existing nickname"
        ))

        seeder.seedAccount()
        val seeded = repository.findByUsername("user")!!
        seeder.seedAccount()

        assertThat(service.login(LoginRequest("user", "user")).id).isEqualTo(existing.id)
        assertThat(seeded.nickname).isEqualTo(existing.nickname)
        assertThat(seeded.createdAtMillis).isEqualTo(existing.createdAtMillis)
        assertThat(repository.findByUsername("user")).isEqualTo(seeded)
    }

    @ParameterizedTest
    @ValueSource(strings = ["local", "dev"])
    fun `seeder is enabled for development profiles`(profile: String) {
        contextRunner(profile).run { context ->
            assertThat(context).hasSingleBean(DevelopmentAccountSeeder::class.java)

            context.publishEvent(ApplicationReadyEvent(
                SpringApplication(), emptyArray(), context.sourceApplicationContext, Duration.ZERO
            ))

            assertThat(service.login(LoginRequest("user", "user")).username).isEqualTo("user")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["prod", "local,prod", "dev,prod", "test"])
    fun `seeder is disabled outside development and whenever production is active`(profiles: String) {
        contextRunner(profiles).run { context ->
            assertThat(context).doesNotHaveBean(DevelopmentAccountSeeder::class.java)
            assertThat(repository.findByUsername("user")).isNull()
        }
    }

    private fun contextRunner(profiles: String) = ApplicationContextRunner()
        .withPropertyValues("spring.profiles.active=$profiles")
        .withBean(AccountRepository::class.java, { repository })
        .withBean(PasswordEncoder::class.java, { encoder })
        .withUserConfiguration(DevelopmentAccountSeeder::class.java)
}
