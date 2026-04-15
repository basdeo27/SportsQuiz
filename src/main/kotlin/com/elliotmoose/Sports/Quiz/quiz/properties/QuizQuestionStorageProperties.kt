package com.elliotmoose.Sports.Quiz.quiz.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.questions")
data class QuizQuestionStorageProperties(
    val storage: String = "local"
)
