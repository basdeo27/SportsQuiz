package com.elliotmoose.Sports.Quiz.quiz.model

import jakarta.validation.constraints.Positive

data class QuizRequest(
    val leagues: Set<League>,
    @field:Positive(message = "numberOfQuestions must be greater than 0.")
    val numberOfQuestions: Int,
    val difficulty: QuizDifficulty = QuizDifficulty.EASY,
    val type: QuizType = QuizType.LOGO,
    val teamIds: Set<String> = emptySet()
)
