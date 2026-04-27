package com.elliotmoose.Sports.Quiz.quiz.model

import java.util.*

data class Quiz(
    val id: String = UUID.randomUUID().toString(),
    val type: QuizType = QuizType.LOGO,
    val difficulty: QuizDifficulty,
    val questions: List<Question>,
    val accountId: String? = null,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
)
