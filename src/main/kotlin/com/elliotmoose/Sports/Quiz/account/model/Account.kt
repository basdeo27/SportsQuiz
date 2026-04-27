package com.elliotmoose.Sports.Quiz.account.model

import java.util.UUID

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val nickname: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
