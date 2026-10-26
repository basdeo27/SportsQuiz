package com.elliotmoose.Sports.Quiz.daily.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.daily.attempts")
data class DailyAttemptProperties(
    val tableName: String = "sports-quiz-daily-attempts",
    val allowDateOverride: Boolean = false,
    val loginRequired: Boolean = false,
    val allowPracticeDateOverride: Boolean = false
)
