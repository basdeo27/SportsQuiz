package com.elliotmoose.Sports.Quiz.quiz.properties

import com.elliotmoose.Sports.Quiz.quiz.model.League
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.settings")
data class QuizSettingsProperties(
    val minQuestions: Int = 10,
    val maxQuestions: Int = 25,
    val disabledLogoLeagues: Set<League> = emptySet(),
    val disabledFaceLeagues: Set<League> = emptySet()
)
