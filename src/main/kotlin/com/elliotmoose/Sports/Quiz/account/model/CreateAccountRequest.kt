package com.elliotmoose.Sports.Quiz.account.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class CreateAccountRequest(
    @field:NotBlank(message = "Email is required.")
    @field:Email(message = "Enter a valid email address.")
    @field:Size(max = 254)
    val email: String,

    @field:NotBlank(message = "Password is required.")
    @field:Size(min = 8, message = "Password must be at least 8 characters.")
    val password: String,

    @field:NotBlank(message = "Nickname is required.")
    @field:Size(max = 40)
    val nickname: String
)
