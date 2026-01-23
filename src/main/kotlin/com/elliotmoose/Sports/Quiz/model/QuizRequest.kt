package com.elliotmoose.Sports.Quiz.model

data class QuizRequest(
    val leagues: Set<League>,
    val numberOfQuestions: Int,
    val difficulty: QuizDifficulty = QuizDifficulty.EASY,
    val type: QuizType = QuizType.LOGO
)
