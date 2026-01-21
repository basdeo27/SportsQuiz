package com.elliotmoose.Sports.Quiz.quiz

data class Quiz(
    val id: String,
    val difficulty: QuizDifficulty,
    val questions: List<Question>
)
