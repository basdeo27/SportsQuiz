package com.elliotmoose.Sports.Quiz.util

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RequestLoggingFilter : OncePerRequestFilter() {

    private val requestLogger = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startNanos = System.nanoTime()
        val method = request.method
        val path = buildPath(request)

        try {
            filterChain.doFilter(request, response)
        } finally {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
            requestLogger.info(
                "{} {} -> {} in {} ms",
                method,
                path,
                response.status,
                String.format("%.1f", elapsedMs)
            )
        }
    }

    private fun buildPath(request: HttpServletRequest): String {
        val query = request.queryString
        return if (query.isNullOrBlank()) {
            request.requestURI
        } else {
            "${request.requestURI}?$query"
        }
    }
}
