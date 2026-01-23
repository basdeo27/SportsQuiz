package com.elliotmoose.Sports.Quiz.model

data class AnswerRequest(
    val quizId: String,
    val questionId: String,
    val answer: String
)
