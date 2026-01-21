package com.elliotmoose.Sports.Quiz.quiz

data class AnswerRequest(
    val quizId: String,
    val questionId: String,
    val answer: String
)
