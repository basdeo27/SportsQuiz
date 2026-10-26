package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.ConfigurationProperties

// POST /v0/ai/**          → ai         (calls external Mistral API)
// POST /v0/account*       → auth       (registration / login brute-force protection)
// POST /v0/quiz*          → quiz-write (active gameplay)
// Everything else         → read       (lightweight GETs)
@ConfigurationProperties("rate-limit")
data class RateLimitProperties(
    val ai: BucketProperties = BucketProperties(capacity = 5, refillPerMinute = 5),
    val auth: BucketProperties = BucketProperties(capacity = 10, refillPerMinute = 10),
    val quizWrite: BucketProperties = BucketProperties(capacity = 30, refillPerMinute = 30),
    val read: BucketProperties = BucketProperties(capacity = 60, refillPerMinute = 60)
)

data class BucketProperties(
    // Set to false to disable this limit without removing the config block.
    val enabled: Boolean = true,
    // Maximum tokens in the bucket (burst allowance).
    val capacity: Long,
    // Tokens added per minute (sustained rate per IP).
    val refillPerMinute: Long
)
