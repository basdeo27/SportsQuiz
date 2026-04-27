package com.elliotmoose.Sports.Quiz.account.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateAccountRequest(
    @field:NotBlank(message = "Username is required.")
    val username: String,

    @field:NotBlank(message = "Password is required.")
    @field:Size(min = 8, message = "Password must be at least 8 characters.")
    val password: String,

    @field:NotBlank(message = "Nickname is required.")
    val nickname: String
)
