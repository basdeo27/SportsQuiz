package com.elliotmoose.Sports.Quiz.results.model

import com.elliotmoose.Sports.Quiz.quiz.model.League
import com.elliotmoose.Sports.Quiz.quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.model.QuizType

data class QuizResult(
    val quizId: String,
    val userId: String,
    val quizType: QuizType = QuizType.LOGO,
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
