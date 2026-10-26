package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.model.DailyScore
import com.elliotmoose.Sports.Quiz.daily.model.DailyScorePage

interface DailyScoreRepository {
    /** Atomically stores the first score for this participant/date; returns the existing score on retries. */
    fun saveFirst(score: DailyScore): DailyScore
    fun findByParticipantAndDate(participantId: String, date: String): DailyScore?
    fun leaderboard(date: String, limit: Int, cursor: String?): DailyScorePage
    fun history(participantId: String, limit: Int, cursor: String?): DailyScorePage
}
