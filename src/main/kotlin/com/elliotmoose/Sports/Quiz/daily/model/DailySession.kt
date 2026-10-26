package com.elliotmoose.Sports.Quiz.daily.model

import java.util.UUID
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity

data class DailySession(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val entry: DailyEntry,
    val hintsRevealed: Int = 1,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val user: UserIdentity? = null,
    val finalScore: Int? = null,
    val completedAtMillis: Long? = null,
    val gaveUp: Boolean = false,
    val submittedScore: DailyScore? = null,
    val ranked: Boolean = true,
    val version: Long = 0
)
