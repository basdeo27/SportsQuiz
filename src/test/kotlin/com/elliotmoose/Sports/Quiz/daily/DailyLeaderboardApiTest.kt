package com.elliotmoose.Sports.Quiz.daily

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import com.elliotmoose.Sports.Quiz.common.mail.LocalEmailSender
import java.util.concurrent.Executors
import java.util.concurrent.Callable

@SpringBootTest(properties = [
    "quiz.storage=local", "quiz.questions.storage=local", "quiz.daily.leaderboard.page-size=2",
    "quiz.daily.attempts.login-required=true",
    "quiz.auth.require-email-verification=true", "quiz.auth.allow-development-login=false",
    "rate-limit.read.enabled=false", "rate-limit.auth.enabled=false", "rate-limit.quiz-write.enabled=false"
])
@AutoConfigureMockMvc
class DailyLeaderboardApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var inbox: LocalEmailSender

    @Test
    fun `member results are automatic private idempotent and preserved once per date`() {
        val token = account()
        val session = start(token = token)
        val result = postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"), token)
        assertThat(result["submittedScore"]["registered"].asBoolean()).isTrue()
        assertThat(result["score"].asInt()).isBetween(0, 1000)
        val first = history(token)["scores"][0]

        postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"), token)
        val repeat = start(token = token)
        postJson("/give-up", mapOf("sessionId" to repeat), token)
        assertThat(history(token)["scores"].size()).isEqualTo(1)
        assertThat(history(token)["scores"][0]).isEqualTo(first)

        val next = start("2099-01-02", token)
        postJson("/give-up", mapOf("sessionId" to next), token)
        val scores = history(token)["scores"]
        assertThat(scores.map { it["date"].asText() }).containsExactly("2099-01-02", "2099-01-01")
        assertThat(scores[0]["score"].asInt()).isZero()
        assertThat(scores[0]["gaveUp"].asBoolean()).isTrue()
        assertThat(history(account())["scores"].size()).isZero()
        mvc.perform(get("/v0/daily/history")).andExpect(status().isUnauthorized)
        mvc.perform(get("/v0/daily/history").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized)
        mvc.perform(get("/v0/daily/scores/me").param("date", "2099-01-01").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andReturn().response.contentAsString.let {
                assertThat(mapper.readTree(it)).isEqualTo(first)
            }
        mvc.perform(post("/v0/daily/hint").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("sessionId" to session))))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `anonymous players cannot start ranked attempts or submit scores`() {
        mvc.perform(post("/v0/daily").contentType(MediaType.APPLICATION_JSON)
            .content("""{"date":"2099-01-01"}""")).andExpect(status().isUnauthorized)
        val session = start(practice = true)
        postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"))
        mvc.perform(post("/v0/daily/scores").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("sessionId" to session)))).andExpect(status().isUnauthorized)
        val token = account()
        mvc.perform(post("/v0/daily/scores").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(mapOf("sessionId" to session))))
            .andExpect(status().isBadRequest)
        assertThat(history(token)["scores"].size()).isZero()
    }

    @Test
    fun `starting again resumes hints timer and completed result instead of creating another attempt`() {
        val token = account()
        val session = start(token = token)
        postJson("/hint", mapOf("sessionId" to session), token)

        assertThat(start(token = token)).isEqualTo(session)
        val resumed = mapper.readTree(mvc.perform(get("/v0/daily/sessions/{id}", session).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        assertThat(resumed["hintsRevealed"].asInt()).isEqualTo(2)
        postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"), token)
        assertThat(start(token = token)).isEqualTo(session)
        val completed = mapper.readTree(mvc.perform(get("/v0/daily/sessions/{id}", session).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        assertThat(completed["completed"].asBoolean()).isTrue()
        assertThat(completed["submittedScore"]["score"].asInt()).isLessThanOrEqualTo(975)
    }

    @Test
    fun `leaderboard and history use configured bounded pages and scoped cursors`() {
        val token = account()
        for (date in listOf("2099-01-01", "2099-01-02")) {
            postJson("/give-up", mapOf("sessionId" to start(date, token)), token)
        }
        val historyFirst = mapper.readTree(mvc.perform(get("/v0/daily/history").param("limit", "1")
            .header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn().response.contentAsString)
        val historyNext = historyFirst["nextCursor"].asText()
        val historyLast = mapper.readTree(mvc.perform(get("/v0/daily/history").param("limit", "1").param("cursor", historyNext)
            .header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertThat(historyLast["scores"][0]["date"].asText()).isEqualTo("2099-01-01")
        assertThat(historyLast["nextCursor"].isNull).isTrue()
        mvc.perform(get("/v0/daily/history").param("cursor", historyNext).header("Authorization", "Bearer ${account()}"))
            .andExpect(status().isBadRequest)

        repeat(4) {
            val member = account()
            val session = start(token = member)
            postJson("/give-up", mapOf("sessionId" to session), member)
        }
        val all = mutableListOf<JsonNode>()
        var cursor: String? = null
        do {
            val request = get("/v0/daily/leaderboard").param("date", "2099-01-01")
            if (cursor != null) request.param("cursor", cursor)
            val page = mapper.readTree(mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
            assertThat(page["scores"].size()).isBetween(1, 2)
            all.addAll(page["scores"].toList())
            cursor = page["nextCursor"].takeUnless { it.isNull }?.asText()
        } while (cursor != null)
        assertThat(all).doesNotHaveDuplicates()
        assertThat(all.all { it["registered"].asBoolean() }).isTrue()
        assertThat(all.map { it["score"].asInt() }).isSortedAccordingTo(reverseOrder())
        for (params in listOf("limit" to "0", "limit" to "101", "limit" to "abc", "date" to "invalid", "cursor" to "invalid")) {
            mvc.perform(get("/v0/daily/leaderboard").param(params.first, params.second)).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `concurrent completions save exactly one member score`() {
        val token = account()
        val session = start(token = token)
        val pool = Executors.newFixedThreadPool(4)
        try {
            pool.invokeAll((1..8).map { Callable {
                postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"), token)
            } }).forEach { assertThat(it.get()["submittedScore"]["registered"].asBoolean()).isTrue() }
        } finally { pool.shutdownNow() }
        assertThat(history(token)["scores"].size()).isEqualTo(1)
    }

    @Test
    fun `account credentials cannot be changed by an unauthenticated caller or another member`() {
        val owner = verifiedAccount()
        val id = owner["id"].asText()
        val body = mapper.writeValueAsString(mapOf("password" to "changed-password"))

        mvc.perform(put("/v0/account/$id").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)
        mvc.perform(put("/v0/account/$id").header("Authorization", "Bearer ${account()}")
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized)
        mvc.perform(get("/v0/account/$id").header("Authorization", "Bearer ${owner["token"].asText()}"))
            .andExpect(status().isOk)
    }

    @Test
    fun `logout revokes the token`() {
        val token = account()
        mvc.perform(post("/v0/account/logout").header("Authorization", "Bearer $token")).andExpect(status().isNoContent)
        mvc.perform(get("/v0/daily/history").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
    }

    private fun account(): String = verifiedAccount()["token"].asText()

    private fun verifiedAccount(): JsonNode {
        val email = "${UUID.randomUUID()}@example.test"
        mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("email" to email, "password" to "password123", "nickname" to "Member"))))
            .andExpect(status().isCreated)
        val token = Regex("verifyEmail=([^\\s]+)").find(inbox.latest(email)!!.text)!!.groupValues[1]
        val response = mvc.perform(post("/v0/account/verify-email").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("token" to token, "password" to "password123")))).andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(response)
    }

    private fun start(date: String = "2099-01-01", token: String? = null, practice: Boolean = false): String {
        val request = post("/v0/daily").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("date" to date, "practice" to practice)))
        if (token != null) request.header("Authorization", "Bearer $token")
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)["sessionId"].asText()
    }

    private fun postJson(path: String, body: Any, token: String? = null): JsonNode {
        val request = post("/v0/daily$path").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))
        if (token != null) request.header("Authorization", "Bearer $token")
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    private fun history(token: String) = mapper.readTree(mvc.perform(get("/v0/daily/history")
        .header("Authorization", "Bearer $token")).andExpect(status().isOk).andReturn().response.contentAsString)
}
