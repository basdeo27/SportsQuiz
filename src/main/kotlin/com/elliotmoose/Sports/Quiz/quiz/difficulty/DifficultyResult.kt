package com.elliotmoose.Sports.Quiz.quiz.difficulty

data class DifficultyResult(
    val matchedAnswer: String?,
    val attemptsRemaining: Int,
    val shouldAdvance: Boolean
)
