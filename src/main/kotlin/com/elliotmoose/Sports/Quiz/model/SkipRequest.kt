package com.elliotmoose.Sports.Quiz.model

data class SkipRequest(
    val quizId: String,
    val questionId: String,
    val hintUsed: Boolean = false
)
