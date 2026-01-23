package com.elliotmoose.Sports.Quiz.model

data class AnswerResponse(
    val correct: Boolean,
    val normalizedAnswer: String,
    val matchedAnswer: String?,
    val correctAnswer: String,
    val attemptsRemaining: Int,
    val shouldAdvance: Boolean
)
