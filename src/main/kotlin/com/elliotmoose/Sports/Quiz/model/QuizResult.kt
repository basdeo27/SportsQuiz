package com.elliotmoose.Sports.Quiz.model

data class QuizResult(
    val quizId: String,
    val userId: String,
    val difficulty: QuizDifficulty,
    val leagues: Set<League>,
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val elapsedSeconds: Long,
    val score: Int,
    val completedAtMillis: Long
)
