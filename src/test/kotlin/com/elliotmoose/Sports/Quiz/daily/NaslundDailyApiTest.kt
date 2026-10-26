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

@SpringBootTest(properties = ["quiz.storage=local", "rate-limit.auth.enabled=false", "rate-limit.read.enabled=false", "rate-limit.quiz-write.enabled=false"])
@AutoConfigureMockMvc
class NaslundDailyApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `five hints reveal in order and an unaccented answer is accepted`() {
        val registration = mvc.perform(post("/v0/account").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("email" to "${UUID.randomUUID()}@example.test", "password" to "password123", "nickname" to "Player"))))
            .andExpect(status().isCreated).andReturn().response.contentAsString
        val token = mapper.readTree(registration)["token"].asText()
        fun post(path: String, body: Any): JsonNode = mapper.readTree(mvc.perform(post("/v0/daily$path")
            .header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        val started = post("", mapOf("date" to "2026-10-24"))
        val id = started["sessionId"].asText()
        assertThat(started["hints"].size()).isEqualTo(1)
        assertThat(started["hints"][0]["type"].asText()).isEqualTo("TEXT")
        assertThat(started.toString()).doesNotContain("ACCOMPLISHMENTS", "Ted Lindsay", "Markus")
        var latest = started
        for ((index, type) in listOf("STAT_LINE", "IMAGE", "IMAGE", "ACCOMPLISHMENTS").withIndex()) {
            latest = post("/hint", mapOf("sessionId" to id))
            assertThat(latest["hints"].size()).isEqualTo(index + 2)
            assertThat(latest["hints"][index + 1]["type"].asText()).isEqualTo(type)
        }
        assertThat(latest["hints"][4]["accomplishments"]["entries"].size()).isEqualTo(4)
        for (index in listOf(2, 3)) {
            val media = mvc.perform(get("/${latest["hints"][index]["url"].asText()}"))
                .andExpect(status().isOk).andReturn().response
            assertThat(media.contentType).startsWith("image/")
            assertThat(media.contentAsByteArray.size).isGreaterThan(1000)
        }
        val answered = post("/answer", mapOf("sessionId" to id, "answer" to "Markus Naslund"))
        assertThat(answered["correct"].asBoolean()).isTrue()
        assertThat(answered["submittedScore"]["date"].asText()).isEqualTo("2026-10-24")
    }
}
