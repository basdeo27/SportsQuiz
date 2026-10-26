package com.elliotmoose.Sports.Quiz.account.model

data class AccountResponse(
    val id: String,
    val username: String,
    val nickname: String,
    val createdAtMillis: Long,
    val token: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id,
            username = account.username,
            nickname = account.nickname,
            createdAtMillis = account.createdAtMillis,
            email = account.email,
            emailVerified = account.emailVerified
        )
    }
}
