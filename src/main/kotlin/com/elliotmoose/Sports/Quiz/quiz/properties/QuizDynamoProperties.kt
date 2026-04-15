package com.elliotmoose.Sports.Quiz.quiz.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.dynamo")
data class QuizDynamoProperties(
    val endpoint: String? = null,
    val region: String = "us-east-1",
    val teamsTableName: String = "sports-quiz-teams",
    val resultsTableName: String = "sports-quiz-results",
    val seed: Boolean = false,
    val accessKey: String? = null,
    val secretKey: String? = null
)
