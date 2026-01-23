package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.dynamo")
data class QuizDynamoProperties(
    val enabled: Boolean = false,
    val endpoint: String? = null,
    val region: String = "us-east-1",
    val tableName: String = "sports-quiz-teams",
    val seed: Boolean = false
)
