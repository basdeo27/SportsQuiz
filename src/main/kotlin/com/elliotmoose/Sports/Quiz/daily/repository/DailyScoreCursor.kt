package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.InvalidDailyRequestException
import java.util.Base64

/** Versioned, query-scoped cursor. No offset scan or server-side cursor state. */
object DailyScoreCursor {
    fun encode(scope: String, vararg keys: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString((listOf("1", scope) + keys).joinToString("\n").toByteArray(Charsets.UTF_8))

    fun decode(cursor: String?, scope: String, keyCount: Int): List<String>? {
        if (cursor == null) return null
        try {
            require(cursor.length <= 2048)
            val parts = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8).split("\n")
            require(parts.size == keyCount + 2 && parts[0] == "1" && parts[1] == scope)
            require(parts.drop(2).all { it.isNotBlank() })
            return parts.drop(2)
        } catch (ex: IllegalArgumentException) {
            throw InvalidDailyRequestException("Invalid pagination cursor for this query.")
        }
    }
}
