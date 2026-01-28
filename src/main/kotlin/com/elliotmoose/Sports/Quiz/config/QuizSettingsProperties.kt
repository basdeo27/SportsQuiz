package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.settings")
data class QuizSettingsProperties(
    val minQuestions: Int = 10,
    val maxQuestions: Int = 25
)
