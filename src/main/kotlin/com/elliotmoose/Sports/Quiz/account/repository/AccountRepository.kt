package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.model.Account

interface AccountRepository {
    fun save(account: Account): Account
    fun create(account: Account): Account
    fun claimEmail(account: Account): Account
    fun findByEmail(email: String): Account?
    fun findById(id: String): Account?
    fun findByUsername(username: String): Account?
    fun delete(id: String)
}
