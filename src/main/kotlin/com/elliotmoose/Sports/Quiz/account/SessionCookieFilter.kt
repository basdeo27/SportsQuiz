package com.elliotmoose.Sports.Quiz.account

import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.config.QuizCorsProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Collections

/** HTTP-only adapter: domain authentication continues to resolve explicit bearer credentials. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class SessionCookieFilter(
    private val properties: AuthenticationProperties,
    private val cors: QuizCorsProperties
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!request.requestURI.startsWith("/v0/")) { chain.doFilter(request, response); return }
        response.setHeader("Cache-Control", "no-store")
        val token = request.cookies?.firstOrNull { it.name == properties.cookieName }?.value
        val unsafe = request.method !in setOf("GET", "HEAD", "OPTIONS")
        val origin = request.getHeader("Origin")
        if (unsafe && ((origin != null && origin !in cors.allowedOrigins) ||
            (token != null && request.getHeader("Authorization") == null && request.getHeader("X-CSRF-Protection") != "1"))) {
            response.status = 403
            response.contentType = "application/json"
            response.writer.write("""{"message":"Request origin or CSRF protection is invalid."}""")
            return
        }
        if (token == null || request.getHeader("Authorization") != null) {
            chain.doFilter(request, response)
            return
        }
        val wrapped = object : HttpServletRequestWrapper(request) {
            override fun getHeader(name: String): String? =
                if (name.equals("Authorization", ignoreCase = true)) "Bearer $token" else super.getHeader(name)
            override fun getHeaders(name: String): java.util.Enumeration<String> =
                if (name.equals("Authorization", ignoreCase = true)) Collections.enumeration(listOf("Bearer $token")) else super.getHeaders(name)
            override fun getHeaderNames(): java.util.Enumeration<String> =
                Collections.enumeration(Collections.list(super.getHeaderNames()).plus("Authorization").distinct())
        }
        chain.doFilter(wrapped, response)
    }
}
