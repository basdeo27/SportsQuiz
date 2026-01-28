package com.elliotmoose.Sports.Quiz.model

data class Quiz(
    val id: String,
    val difficulty: QuizDifficulty,
    val questions: List<Question>,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null
)
