package com.elliotmoose.Sports.Quiz.monitoring

import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import com.elliotmoose.Sports.Quiz.common.monitoring.UsageMonitor
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(properties = ["quiz.storage=local", "rate-limit.read.enabled=false"])
@AutoConfigureMockMvc
class MonitoringApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var monitoring: UsageMonitor

    @Test
    fun `summary is protected counts completions once and groups endpoint errors`() {
        mvc.perform(get("/v0/monitoring")).andExpect(status().isNotFound)
        mvc.perform(get("/v0/monitoring").header("X-Monitoring-Key", "wrong")).andExpect(status().isNotFound)
        val id = UUID.randomUUID().toString()
        monitoring.quizCompleted(id, CompletionType.LOGO, 1700000000000)
        monitoring.quizCompleted(id, CompletionType.LOGO, 1700000000000)
        mvc.perform(get("/v0/account/me").queryParam("private", "do-not-log-this"))
            .andExpect(status().isUnauthorized)
        val response = mvc.perform(get("/v0/monitoring").header("X-Monitoring-Key", "dev-monitoring"))
            .andExpect(status().isOk).andReturn().response
        val summary = mapper.readTree(response.contentAsString)
        assertThat(summary["totalCompleted"].asLong()).isEqualTo(1)
        assertThat(summary["completedByType"]["LOGO"].asLong()).isEqualTo(1)
        assertThat(summary["endpointErrors"].toList().any { it["endpoint"].asText() == "/v0/account/me" && it["status"].asInt() == 401 }).isTrue()
        assertThat(response.getHeader("X-Request-ID")).isNotBlank()
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store")
        mvc.perform(get("/v0/monitoring").header("X-Monitoring-Key", "dev-monitoring").param("date", "invalid"))
            .andExpect(status().isBadRequest)
    }
}
