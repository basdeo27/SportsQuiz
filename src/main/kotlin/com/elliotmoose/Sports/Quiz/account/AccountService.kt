package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.model.AccountResponse
import com.elliotmoose.Sports.Quiz.account.model.CreateAccountRequest
import com.elliotmoose.Sports.Quiz.account.model.LoginRequest
import com.elliotmoose.Sports.Quiz.account.model.UpdateAccountRequest
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun createAccount(request: CreateAccountRequest): AccountResponse {
        if (accountRepository.findByUsername(request.username) != null) {
            throw AccountAlreadyExistsException(request.username)
        }
        val account = Account(
            username = request.username,
            passwordHash = passwordEncoder.encode(request.password),
            nickname = request.nickname
        )
        return AccountResponse.from(accountRepository.save(account))
    }

    fun login(request: LoginRequest): AccountResponse {
        val account = accountRepository.findByUsername(request.username)
            ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(request.password, account.passwordHash)) {
            throw InvalidCredentialsException()
        }
        return AccountResponse.from(account)
    }

    fun getAccount(id: String): AccountResponse {
        val account = accountRepository.findById(id) ?: throw AccountNotFoundException(id)
        return AccountResponse.from(account)
    }

    fun updateAccount(id: String, request: UpdateAccountRequest): AccountResponse {
        val existing = accountRepository.findById(id) ?: throw AccountNotFoundException(id)
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
