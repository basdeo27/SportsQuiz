package com.elliotmoose.Sports.Quiz.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class RateLimitInterceptor(private val properties: RateLimitProperties) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(RateLimitInterceptor::class.java)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val path = request.requestURI

        if (path == "/ping") return true

        val group = resolveGroup(request)
        val props = propsForGroup(group)

        if (!props.enabled) return true

        val ip = resolveIp(request)
        val bucket = buckets.computeIfAbsent("$group:$ip") { createBucket(props) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)

        if (probe.isConsumed) return true

        val retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWaitForRefill) + 1
        log.warn("Rate limit exceeded for ip={} group={} path={}", ip, group, path)
        response.status = 429
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.contentType = "application/json"
        response.writer.write("""{"message":"Too many requests. Please try again in $retryAfterSeconds second(s)."}""")
        return false
    }

    private fun resolveGroup(request: HttpServletRequest): String {
        val path = request.requestURI
        val method = request.method
        return when {
            path.startsWith("/v0/ai/") -> "ai"
            method == "POST" && path.startsWith("/v0/account") -> "auth"
            method == "POST" && (path.startsWith("/v0/quiz") || path.startsWith("/v0/daily")) -> "quiz-write"
            else -> "read"
        }
    }

    private fun propsForGroup(group: String): BucketProperties = when (group) {
        "ai" -> properties.ai
        "auth" -> properties.auth
        "quiz-write" -> properties.quizWrite
        else -> properties.read
    }

    private fun createBucket(props: BucketProperties): Bucket {
        val bandwidth = Bandwidth.classic(
            props.capacity,
            Refill.greedy(props.refillPerMinute, Duration.ofMinutes(1))
        )
        return Bucket.builder().addLimit(bandwidth).build()
    }

    private fun resolveIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr
    }
}
