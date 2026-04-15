package com.elliotmoose.Sports.Quiz.model

import java.util.*

data class Quiz(
    val id: String = UUID.randomUUID().toString(),
    val difficulty: QuizDifficulty,
    val questions: List<Question>,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
)
