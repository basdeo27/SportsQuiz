package com.elliotmoose.Sports.Quiz.model

import jakarta.validation.constraints.NotBlank

data class AnswerRequest(
    @field:NotBlank(message = "quizId must not be blank.")
    val quizId: String,
    @field:NotBlank(message = "questionId must not be blank.")
    val questionId: String,
    @field:NotBlank(message = "answer must not be blank.")
    val answer: String,
    val hintUsed: Boolean = false
)
