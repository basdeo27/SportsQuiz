package com.elliotmoose.Sports.Quiz.quiz

data class Question(
    val id: String,
    val league: League,
    val logoUrl: String,
    val fullName: String,
    val correctAnswers: Set<String>
)
