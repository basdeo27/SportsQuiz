package com.elliotmoose.Sports.Quiz.quiz.model

data class TeamEntry(
    val id: String,
    val name: String,
    val logoUrl: String,
    val answers: List<String> = emptyList(),
    val hints: Map<QuizDifficulty, List<String>> = emptyMap(),
    val hint: String? = null
)
