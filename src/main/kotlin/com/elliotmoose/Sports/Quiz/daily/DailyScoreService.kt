package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.daily.model.*
import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import com.elliotmoose.Sports.Quiz.daily.repository.DailyScoreRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.time.Clock

@Service
@EnableConfigurationProperties(DailyLeaderboardProperties::class)
class DailyScoreService(
    private val repository: DailyScoreRepository,
    private val properties: DailyLeaderboardProperties,
    private val clock: Clock
) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(properties.zone)))

    fun record(session: DailySession, user: UserIdentity): DailyScore {
        if (!session.ranked || session.user?.id != user.id) throw InvalidDailyRequestException("Only your ranked attempt can enter the leaderboard.")
        if (!session.completed || session.finalScore == null || session.completedAtMillis == null) {
            throw InvalidDailyRequestException("Complete the challenge before submitting a score.")
        }
        val displayName = user.displayName.trim().take(40)
        if (displayName.isBlank() || displayName.length > 40 || displayName.any(Char::isISOControl)) {
            throw InvalidDailyRequestException("Enter a display name between 1 and 40 characters.")
        }
        return repository.saveFirst(DailyScore(
            participantId = "USER#${user.id}",
            date = session.date, sessionId = session.id, displayName = displayName,
            accountId = user.id, score = session.finalScore,
            elapsedSeconds = (session.completedAtMillis - session.startTimeMillis).coerceAtLeast(0) / 1000,
            hintsRevealed = session.hintsRevealed, completedAtMillis = session.completedAtMillis, gaveUp = session.gaveUp
        ))
    }

    fun leaderboard(date: String?, limit: Int?, cursor: String?): DailyScorePage =
        repository.leaderboard(parseDate(date ?: today().toString()).toString(), pageSize(limit), cursor)

    fun history(user: UserIdentity, limit: Int?, cursor: String?): DailyScorePage =
        repository.history("USER#${user.id}", pageSize(limit), cursor)

    fun scoreForDay(user: UserIdentity, date: String): DailyScore? =
        repository.findByParticipantAndDate("USER#${user.id}", parseDate(date).toString())

    fun parseDate(date: String): LocalDate = try { LocalDate.parse(date) } catch (ex: java.time.format.DateTimeParseException) {
        throw InvalidDailyRequestException("Expected a date in YYYY-MM-DD format.")
    }

    private fun pageSize(limit: Int?): Int {
        val resolved = limit ?: properties.pageSize
        if (resolved !in 1..properties.maxPageSize) {
            throw InvalidDailyRequestException("Page size must be between 1 and ${properties.maxPageSize}.")
        }
        return resolved
    }
}
