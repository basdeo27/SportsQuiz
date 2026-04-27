package com.elliotmoose.Sports.Quiz.account.model

import jakarta.validation.constraints.Size

data class UpdateAccountRequest(
    val nickname: String? = null,

    @field:Size(min = 8, message = "Password must be at least 8 characters.")
    val password: String? = null
)
