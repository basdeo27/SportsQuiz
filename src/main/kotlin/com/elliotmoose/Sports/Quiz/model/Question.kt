package com.elliotmoose.Sports.Quiz.model

data class Question(
    val id: String,
    val league: League,
    val logoUrl: String,
    val fullName: String,
    val correctAnswers: Set<String>,
    val submittedAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val isSkipped: Boolean? = null,
    val hinted: Boolean = false
)
