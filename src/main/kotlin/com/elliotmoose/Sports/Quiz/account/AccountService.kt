package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.model.AccountResponse
import com.elliotmoose.Sports.Quiz.account.model.CreateAccountRequest
import com.elliotmoose.Sports.Quiz.account.model.LoginRequest
import com.elliotmoose.Sports.Quiz.account.model.UpdateAccountRequest
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: AuthenticationProperties
) {

    fun createAccount(request: CreateAccountRequest): AccountResponse {
        validatePassword(request.password)
        val email = request.email.trim().lowercase()
        val account = Account(username = email, email = email,
            passwordHash = passwordEncoder.encode(request.password), nickname = request.nickname.trim(),
            emailVerified = !properties.requireEmailVerification)
        return AccountResponse.from(accountRepository.create(account))
    }

    fun login(request: LoginRequest): AccountResponse {
        if (request.password.toByteArray(Charsets.UTF_8).size > 72) throw InvalidCredentialsException()
        val account = if (properties.allowDevelopmentLogin && request.email == "user") {
            accountRepository.findByUsername("user")
        } else accountRepository.findByEmail(request.email.trim().lowercase())
        if (account == null || !passwordEncoder.matches(request.password, account.passwordHash)) throw InvalidCredentialsException()
        if (properties.requireEmailVerification && !account.emailVerified) throw EmailVerificationRequiredException()
        return AccountResponse.from(account)
    }

    fun linkLegacyEmail(username: String, password: String, email: String): AccountResponse {
        if (password.toByteArray(Charsets.UTF_8).size > 72) throw InvalidCredentialsException()
        val account = accountRepository.findByUsername(username.trim().lowercase()) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, account.passwordHash)) throw InvalidCredentialsException()
        if (account.email != null) throw InvalidAccountRequestException("This account already has an email. Log in with that email address.")
        return AccountResponse.from(accountRepository.claimEmail(account.copy(email = email.trim().lowercase(),
            emailVerified = !properties.requireEmailVerification)))
    }

    private fun validatePassword(password: String) {
        if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72) {
            throw InvalidAccountRequestException("Password must be at least 8 characters and at most 72 UTF-8 bytes.")
        }
    }

    fun getAccount(id: String): AccountResponse {
        val account = accountRepository.findById(id) ?: throw AccountNotFoundException(id)
        return AccountResponse.from(account)
    }

    fun updateAccount(id: String, request: UpdateAccountRequest): AccountResponse {
        val existing = accountRepository.findById(id) ?: throw AccountNotFoundException(id)
        request.password?.let(::validatePassword)
        val updated = existing.copy(
            nickname = request.nickname ?: existing.nickname,
            passwordHash = request.password?.let { passwordEncoder.encode(it) } ?: existing.passwordHash
        )
        return AccountResponse.from(accountRepository.save(updated))
    }

    fun deleteAccount(id: String) {
        accountRepository.findById(id) ?: throw AccountNotFoundException(id)
        accountRepository.delete(id)
    }
}
