package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.repository.AccountRepository
import com.elliotmoose.Sports.Quiz.common.mail.LocalEmailSender
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(properties = ["quiz.storage=local", "quiz.auth.require-email-verification=true",
    "quiz.auth.allow-development-login=false", "rate-limit.auth.enabled=false", "rate-limit.read.enabled=false"])
@AutoConfigureMockMvc
class EmailAuthenticationApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var inbox: LocalEmailSender
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var encoder: PasswordEncoder

    @Test
    fun `production-style signup requires email verification and normalizes email for login`() {
        val email = "${UUID.randomUUID()}@example.test"
        val registration = mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email, "password" to "password123", "nickname" to "Player"))))
            .andExpect(status().isCreated).andReturn().response
        val account = mapper.readTree(registration.contentAsString)
        assertThat(account["emailVerified"].asBoolean()).isFalse()
        assertThat(account["token"].isNull).isTrue()
        assertThat(registration.getHeader("Set-Cookie")).isNull()
        assertThat(registration.contentAsString).doesNotContain("verifyEmail=")
        mvc.perform(post("/v0/account/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email, "password" to "password123")))).andExpect(status().isForbidden)
        mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email.uppercase(), "password" to "password123", "nickname" to "Other"))))
            .andExpect(status().isConflict)

        val token = verificationToken(email)
        val verified = mvc.perform(post("/v0/account/verify-email").contentType(MediaType.APPLICATION_JSON)
            .header("X-Session-Mode", "cookie").content(body(mapOf("token" to token, "password" to "password123"))))
            .andExpect(status().isOk).andReturn().response
        assertThat(mapper.readTree(verified.contentAsString)["emailVerified"].asBoolean()).isTrue()
        assertThat(mapper.readTree(verified.contentAsString)["token"].isNull).isTrue()
        assertThat(verified.getHeader("Set-Cookie")).contains("HttpOnly", "Path=/v0", "SameSite=Lax")
        mvc.perform(post("/v0/account/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("token" to token, "password" to "password123")))).andExpect(status().isBadRequest)
        mvc.perform(post("/v0/account/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email.uppercase(), "password" to "password123")))).andExpect(status().isOk)
        mvc.perform(post("/v0/account/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to "user", "password" to "user")))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `cookie sessions restore identity enforce CSRF and revoke at logout`() {
        val (user, cookie) = registeredCookie()
        mvc.perform(get("/v0/account/me").cookie(cookie)).andExpect(status().isOk)
        mvc.perform(post("/v0/account/logout").cookie(cookie)).andExpect(status().isForbidden)
        mvc.perform(post("/v0/account/logout").cookie(cookie).header("X-CSRF-Protection", "1")
            .header("Origin", "https://untrusted.example")).andExpect(status().isForbidden)
        val logout = mvc.perform(post("/v0/account/logout").cookie(cookie).header("X-CSRF-Protection", "1")
            .header("Origin", "http://localhost:5173")).andExpect(status().isNoContent).andReturn().response
        assertThat(logout.getHeader("Set-Cookie")).contains("Max-Age=0")
        mvc.perform(get("/v0/account/me").cookie(cookie)).andExpect(status().isUnauthorized)
        assertThat(user["id"].asText()).isNotBlank()
    }

    @Test
    fun `password changes invalidate all previous sessions`() {
        val (user, cookie) = registeredCookie()
        val email = user["email"].asText()
        val login = mvc.perform(post("/v0/account/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email, "password" to "password123")))).andExpect(status().isOk).andReturn().response
        val bearer = mapper.readTree(login.contentAsString)["token"].asText()

        mvc.perform(put("/v0/account/${user["id"].asText()}").cookie(cookie).header("X-CSRF-Protection", "1")
            .contentType(MediaType.APPLICATION_JSON).content(body(mapOf("password" to "new-password123")))).andExpect(status().isOk)

        mvc.perform(get("/v0/account/me").cookie(cookie)).andExpect(status().isUnauthorized)
        mvc.perform(get("/v0/account/me").header("Authorization", "Bearer $bearer")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `existing username accounts can link and verify email while preserving identity`() {
        val username = UUID.randomUUID().toString()
        val existing = accounts.save(Account(username = username, passwordHash = encoder.encode("password123"), nickname = "Legacy"))
        val email = "${UUID.randomUUID()}@example.test"
        mvc.perform(post("/v0/account/link-email").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("username" to username, "password" to "wrong", "email" to email)))).andExpect(status().isUnauthorized)
        mvc.perform(post("/v0/account/link-email").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("username" to username, "password" to "password123", "email" to email)))).andExpect(status().isOk)
        val verified = mvc.perform(post("/v0/account/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("token" to verificationToken(email), "password" to "password123")))).andExpect(status().isOk).andReturn().response
        assertThat(mapper.readTree(verified.contentAsString)["id"].asText()).isEqualTo(existing.id)
    }

    @Test
    fun `verification links cannot be used as bearer sessions and resends are throttled per email`() {
        val email = "${UUID.randomUUID()}@example.test"
        mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email, "password" to "password123", "nickname" to "Player"))))
            .andExpect(status().isCreated)
        val token = verificationToken(email)
        mvc.perform(get("/v0/account/me").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
        mvc.perform(post("/v0/account/verification").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email)))).andExpect(status().isOk)
        assertThat(verificationToken(email)).isEqualTo(token)
        mvc.perform(post("/v0/account/verification").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to "unknown@example.test")))).andExpect(status().isOk)
    }

    private fun registeredCookie(): Pair<JsonNode, Cookie> {
        val email = "${UUID.randomUUID()}@example.test"
        mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(body(mapOf("email" to email, "password" to "password123", "nickname" to "Player"))))
            .andExpect(status().isCreated)
        val response = mvc.perform(post("/v0/account/verify-email").header("X-Session-Mode", "cookie")
            .contentType(MediaType.APPLICATION_JSON).content(body(mapOf("token" to verificationToken(email), "password" to "password123"))))
            .andExpect(status().isOk).andReturn().response
        val raw = response.getHeader("Set-Cookie")!!.substringBefore(';').substringAfter('=')
        return mapper.readTree(response.contentAsString) to Cookie("sports_quiz_session", raw)
    }
    private fun verificationToken(email: String): String = Regex("verifyEmail=([^\\s]+)").find(inbox.latest(email)!!.text)!!.groupValues[1]
    private fun body(value: Any): String = mapper.writeValueAsString(value)
}
