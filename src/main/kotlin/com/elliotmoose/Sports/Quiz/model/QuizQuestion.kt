package com.elliotmoose.Sports.Quiz.model

data class QuizQuestion(
    val id: String,
    val league: League,
    val logoUrl: String,
    val fullName: String,
    val hints: Map<QuizDifficulty, List<String>> = emptyMap()
)
