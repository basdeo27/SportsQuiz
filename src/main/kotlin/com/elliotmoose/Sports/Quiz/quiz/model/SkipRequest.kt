package com.elliotmoose.Sports.Quiz.quiz.model

import jakarta.validation.constraints.NotBlank

data class SkipRequest(
    @field:NotBlank(message = "quizId must not be blank.")
    val quizId: String,
    @field:NotBlank(message = "questionId must not be blank.")
    val questionId: String,
    val hintUsed: Boolean = false
)
