package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.model.Account

interface AccountRepository {
    fun save(account: Account): Account
    fun findById(id: String): Account?
    fun findByUsername(username: String): Account?
    fun delete(id: String)
}
