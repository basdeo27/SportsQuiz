package com.elliotmoose.Sports.Quiz.util

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import com.elliotmoose.Sports.Quiz.common.monitoring.UsageMonitor
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class RequestLoggingFilterTest {
    @Test
    fun `logs endpoint failures with correlation IDs but never query credentials`() {
        val failures = mutableListOf<String>()
        val monitoring = object : UsageMonitor {
            override fun quizCompleted(id: String, type: CompletionType, completedAtMillis: Long) {}
            override fun endpointError(method: String, route: String, status: Int) { failures.add("$method $route $status") }
        }
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val request = MockHttpServletRequest("GET", "/v0/test/123").apply {
                queryString = "token=do-not-log&email=private@example.test"
                setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v0/test/{id}")
            }
            val response = MockHttpServletResponse()
            val filter = RequestLoggingFilter(jacksonObjectMapper(), monitoring)

            assertThatThrownBy { filter.doFilter(request, response) { _, _ -> throw IllegalStateException("test failure") } }
                .isInstanceOf(IllegalStateException::class.java)

            assertThat(response.status).isEqualTo(500)
            assertThat(response.getHeader("X-Request-ID")).isNotBlank()
            assertThat(failures).containsExactly("GET /v0/test/{id} 500")
            assertThat(appender.list.single().formattedMessage).contains("http_request", "SERVER_ERROR")
                .doesNotContain("do-not-log", "private@example.test", "token=")
        } finally { logger.detachAppender(appender); appender.stop() }
    }
}
