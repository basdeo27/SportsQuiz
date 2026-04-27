package com.elliotmoose.Sports.Quiz.account.model

data class AccountResponse(
    val id: String,
    val username: String,
    val nickname: String,
    val createdAtMillis: Long
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id,
            username = account.username,
            nickname = account.nickname,
            createdAtMillis = account.createdAtMillis
        )
    }
}
