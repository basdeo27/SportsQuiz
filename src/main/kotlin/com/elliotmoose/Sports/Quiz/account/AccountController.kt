package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.AccountResponse
import com.elliotmoose.Sports.Quiz.account.model.CreateAccountRequest
import com.elliotmoose.Sports.Quiz.account.model.LoginRequest
import com.elliotmoose.Sports.Quiz.account.model.UpdateAccountRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/account")
class AccountController(private val accountService: AccountService) {

    @PostMapping
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): ResponseEntity<AccountResponse> {
        return ResponseEntity(accountService.createAccount(request), HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AccountResponse {
        return accountService.login(request)
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: String): AccountResponse {
        return accountService.getAccount(id)
    }

    @PutMapping("/{id}")
    fun updateAccount(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateAccountRequest
    ): AccountResponse {
        return accountService.updateAccount(id, request)
    }

    @DeleteMapping("/{id}")
    fun deleteAccount(@PathVariable id: String): ResponseEntity<Void> {
        accountService.deleteAccount(id)
        return ResponseEntity.noContent().build()
    }
}
