package com.elliotmoose.Sports.Quiz.quiz

data class AnswerResponse(
    val correct: Boolean,
    val normalizedAnswer: String,
    val matchedAnswer: String?
)
