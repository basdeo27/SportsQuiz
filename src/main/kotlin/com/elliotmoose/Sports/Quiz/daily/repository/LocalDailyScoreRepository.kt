package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.model.DailyScore
import com.elliotmoose.Sports.Quiz.daily.model.DailyScorePage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalDailyScoreRepository : DailyScoreRepository {
    private val scores = ConcurrentHashMap<Pair<String, String>, DailyScore>()

    override fun saveFirst(score: DailyScore): DailyScore =
        scores.putIfAbsent(score.participantId to score.date, score) ?: score

    override fun findByParticipantAndDate(participantId: String, date: String): DailyScore? = scores[participantId to date]

    override fun leaderboard(date: String, limit: Int, cursor: String?): DailyScorePage {
        val scope = "day:$date"
        val after = DailyScoreCursor.decode(cursor, scope, 3)?.get(2)
        val page = scores.values.filter { it.accountId != null && it.date == date && (after == null || it.rankingKey > after) }
            .sortedBy { it.rankingKey }.take(limit + 1)
        val last = page.take(limit).lastOrNull()
        return DailyScorePage(page.take(limit), if (page.size > limit && last != null)
            DailyScoreCursor.encode(scope, last.participantId, last.date, last.rankingKey) else null)
    }

    override fun history(participantId: String, limit: Int, cursor: String?): DailyScorePage {
        val scope = "user:$participantId"
        val before = DailyScoreCursor.decode(cursor, scope, 1)?.single()
        val page = scores.values.filter { it.participantId == participantId && (before == null || it.date < before) }
            .sortedByDescending { it.date }.take(limit + 1)
        return DailyScorePage(page.take(limit), if (page.size > limit)
            DailyScoreCursor.encode(scope, page[limit - 1].date) else null)
    }
}
