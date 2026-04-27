package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.model.Account
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalAccountRepository : AccountRepository {

    private val accounts = ConcurrentHashMap<String, Account>()

    override fun save(account: Account): Account {
        accounts[account.id] = account
        return account
    }

    override fun findById(id: String): Account? = accounts[id]

    override fun findByUsername(username: String): Account? =
        accounts.values.find { it.username.equals(username, ignoreCase = true) }

    override fun delete(id: String) {
        accounts.remove(id)
    }
}
