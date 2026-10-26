package com.elliotmoose.Sports.Quiz.daily

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.elliotmoose.Sports.Quiz.daily.properties.DailyAttemptProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = [
    "quiz.storage=local", "quiz.questions.storage=local", "quiz.daily.attempts.allow-date-override=true",
    "rate-limit.read.enabled=false", "rate-limit.auth.enabled=false", "rate-limit.quiz-write.enabled=false"
])
@AutoConfigureMockMvc
class GuestDailyApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var properties: DailyAttemptProperties

    @Test
    fun `default configuration permits guest completion without exposing history or leaderboard writes`() {
        assertThat(properties.loginRequired).isFalse()
        val start = postJson("", mapOf("date" to "2099-01-01"))
        assertThat(start["ranked"].asBoolean()).isFalse()
        val session = start["sessionId"].asText()
        val hint = postJson("/hint", mapOf("sessionId" to session))
        assertThat(hint["hintsRevealed"].asInt()).isEqualTo(2)
        val result = postJson("/answer", mapOf("sessionId" to session, "answer" to "Jordan"))
        assertThat(result["correct"].asBoolean()).isTrue()
        assertThat(result["score"].asInt()).isBetween(0, 975)
        assertThat(result["submittedScore"].isNull).isTrue()
        val resumed = mapper.readTree(mvc.perform(get("/v0/daily/sessions/{id}", session))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        assertThat(resumed["completed"].asBoolean()).isTrue()
        assertThat(resumed["ranked"].asBoolean()).isFalse()
        mvc.perform(get("/v0/daily/history")).andExpect(status().isUnauthorized)
        mvc.perform(post("/v0/daily/scores").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("sessionId" to session))))
            .andExpect(status().isUnauthorized)
        val leaderboard = mapper.readTree(mvc.perform(get("/v0/daily/leaderboard").param("date", "2099-01-01"))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        assertThat(leaderboard["scores"].size()).isZero()
    }

    private fun postJson(path: String, body: Any): JsonNode = mapper.readTree(
        mvc.perform(post("/v0/daily$path").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk).andReturn().response.contentAsString
    )
}
