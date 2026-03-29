package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.cors")
data class QuizCorsProperties(
    val allowedOrigins: List<String> = emptyList()
)
