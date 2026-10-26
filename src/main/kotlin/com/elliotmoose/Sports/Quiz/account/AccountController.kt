package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.AccountResponse
import com.elliotmoose.Sports.Quiz.account.model.CreateAccountRequest
import com.elliotmoose.Sports.Quiz.account.model.LoginRequest
import com.elliotmoose.Sports.Quiz.account.model.UpdateAccountRequest
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import java.time.Duration
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/account")
class AccountController(
    private val accountService: AccountService,
    private val authentication: AccountAuthentication,
    private val properties: AuthenticationProperties,
    private val verification: EmailVerificationService
) {

    @PostMapping
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest, http: HttpServletRequest, response: HttpServletResponse): ResponseEntity<AccountResponse> {
        val account = accountService.createAccount(request)
        if (!account.emailVerified) {
            verification.send(account.id)
            return ResponseEntity(account, HttpStatus.CREATED)
        }
        return ResponseEntity(signIn(account, http, response), HttpStatus.CREATED)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, http: HttpServletRequest, response: HttpServletResponse): AccountResponse {
        val account = accountService.login(request)
        return signIn(account, http, response)
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody request: VerificationRequest, http: HttpServletRequest, response: HttpServletResponse): AccountResponse =
        signIn(AccountResponse.from(verification.verify(request.token, request.password)), http, response)

    @PostMapping("/verification")
    fun resendVerification(@Valid @RequestBody request: EmailRequest): Map<String, String> {
        verification.resend(request.email)
        return mapOf("message" to "If this email needs verification, a new link has been sent.")
    }

    @PostMapping("/link-email")
    fun linkEmail(@Valid @RequestBody request: LinkEmailRequest, http: HttpServletRequest, response: HttpServletResponse): AccountResponse {
        val account = accountService.linkLegacyEmail(request.username, request.password, request.email)
        if (!account.emailVerified) { verification.send(account.id); return account }
        return signIn(account, http, response)
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization", required = false) authorization: String?, response: HttpServletResponse): ResponseEntity<Void> {
        authentication.revoke(authorization)
        setCookie(response, "", Duration.ZERO)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun currentAccount(@RequestHeader("Authorization", required = false) authorization: String?): AccountResponse {
        val user = authentication.resolve(authorization) ?: throw AuthenticationRequiredException()
        return accountService.getAccount(user.id)
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: String,
        @RequestHeader("Authorization", required = false) authorization: String?): AccountResponse {
        requireOwner(id, authorization)
        return accountService.getAccount(id)
    }

    @PutMapping("/{id}")
    fun updateAccount(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateAccountRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): AccountResponse {
        requireOwner(id, authorization)
        return accountService.updateAccount(id, request)
    }

    @DeleteMapping("/{id}")
    fun deleteAccount(@PathVariable id: String,
        @RequestHeader("Authorization", required = false) authorization: String?): ResponseEntity<Void> {
        requireOwner(id, authorization)
        accountService.deleteAccount(id)
        return ResponseEntity.noContent().build()
    }

    private fun signIn(account: AccountResponse, http: HttpServletRequest, response: HttpServletResponse): AccountResponse {
        // Revoke a previous session on this browser when logging in again.
        authentication.revoke(http.getHeader("Authorization"))
        val token = authentication.issue(account.id)
        setCookie(response, token, Duration.ofHours(properties.sessionHours))
        return account.copy(token = if (http.getHeader("X-Session-Mode") == "cookie") null else token)
    }

    private fun setCookie(response: HttpServletResponse, token: String, maxAge: Duration) {
        response.addHeader("Set-Cookie", ResponseCookie.from(properties.cookieName, token)
            .httpOnly(true).secure(properties.secureCookie).sameSite(properties.sameSite)
            .path("/v0").maxAge(maxAge).build().toString())
    }

    private fun requireOwner(id: String, authorization: String?) {
        if (authentication.resolve(authorization)?.id != id) throw AuthenticationRequiredException()
    }
}


data class VerificationRequest(val token: String, val password: String)
data class EmailRequest(@field:jakarta.validation.constraints.Email @field:jakarta.validation.constraints.NotBlank val email: String)
data class LinkEmailRequest(
    @field:jakarta.validation.constraints.NotBlank val username: String,
    @field:jakarta.validation.constraints.NotBlank val password: String,
    @field:jakarta.validation.constraints.Email @field:jakarta.validation.constraints.NotBlank val email: String
)
