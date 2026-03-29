package com.elliotmoose.Sports.Quiz.model

data class Question(
    val id: String,
    val league: League,
    val logoUrl: String,
    val fullName: String,
    val teamId: String? = null,
    val hint: String? = null,
    val correctAnswers: Set<String>,
    val submittedAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val isSkipped: Boolean? = null,
    val hinted: Boolean = false
)
