package com.elliotmoose.Sports.Quiz.config

import com.elliotmoose.Sports.Quiz.model.League
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.settings")
data class QuizSettingsProperties(
    val minQuestions: Int = 10,
    val maxQuestions: Int = 25,
    val disabledLeagues: Set<League> = emptySet()
)
