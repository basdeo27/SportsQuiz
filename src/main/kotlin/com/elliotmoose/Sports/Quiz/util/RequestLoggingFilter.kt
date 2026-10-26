package com.elliotmoose.Sports.Quiz.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.elliotmoose.Sports.Quiz.common.monitoring.UsageMonitor
import org.springframework.web.servlet.HandlerMapping
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLoggingFilter(private val mapper: ObjectMapper, private val monitoring: UsageMonitor) : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val started = System.nanoTime()
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-ID", requestId)
        try { chain.doFilter(request, response) }
        catch (ex: Exception) {
            if (!response.isCommitted) response.status = 500
            throw ex
        }
        finally {
            val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString() ?: "unmatched"
            if (response.status >= 400) monitoring.endpointError(request.method.takeIf { it in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS") } ?: "OTHER", route, response.status)
            val event = mapper.writeValueAsString(mapOf(
                "event" to "http_request", "requestId" to requestId, "method" to request.method,
                // Deliberately omit query strings, headers and bodies: they may contain credentials or email addresses.
                "path" to request.requestURI, "status" to response.status,
                "durationMs" to (System.nanoTime() - started) / 1_000_000,
                "outcome" to if (response.status >= 500) "SERVER_ERROR" else if (response.status >= 400) "CLIENT_ERROR" else "SUCCESS"
            ))
            if (response.status >= 500) requestLogger.error("{}", event) else requestLogger.info("{}", event)
            MDC.remove("requestId")
        }
    }
}
