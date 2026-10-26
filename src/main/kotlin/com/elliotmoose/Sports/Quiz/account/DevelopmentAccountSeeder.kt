package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
@Profile("(local | dev) & !prod")
class DevelopmentAccountSeeder(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) {
    // Run after startup runners have created the DynamoDB tables, when applicable.
    @EventListener(ApplicationReadyEvent::class)
    fun seedAccount() {
        val existing = accountRepository.findByUsername("user")
        if (existing != null && passwordEncoder.matches("user", existing.passwordHash) && existing.email != null && existing.emailVerified) {
            return
        }

        val account = existing?.copy(passwordHash = passwordEncoder.encode("user"), email = existing.email ?: "user@localhost.test", emailVerified = true)
            ?: Account(
                username = "user",
                passwordHash = passwordEncoder.encode("user"),
                nickname = "Dev User",
                email = "user@localhost.test",
                emailVerified = true
            )
        when {
            existing == null -> accountRepository.create(account)
            existing.email == null -> accountRepository.claimEmail(account)
            else -> accountRepository.save(account)
        }
    }
}
