package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.daily.model.DailyAnswerRequest
import com.elliotmoose.Sports.Quiz.daily.model.DailyScoreSubmissionRequest
import com.elliotmoose.Sports.Quiz.daily.properties.DailyAttemptProperties
import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import com.elliotmoose.Sports.Quiz.daily.repository.LocalDailyAttemptRepository
import com.elliotmoose.Sports.Quiz.daily.repository.LocalDailyScoreRepository
import com.elliotmoose.Sports.Quiz.monitoring.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DailyLiveSelectionTest {
    private val mapper = jacksonObjectMapper()
    private val repository = DailyRepository(mapper)
    private val date = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC)
    private val results = LocalDailyScoreRepository()
    private val attempts = LocalDailyAttemptRepository()
    private val scores = DailyScoreService(results, DailyLeaderboardProperties(), clock)
    private val properties = DailyAttemptProperties(allowPracticeDateOverride = true)
    private val monitor = MonitoringService(LocalMonitoringRepository(), MonitoringProperties(), clock)
    private fun service() = DailyService(repository, com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher(),
        scores, attempts, properties, clock, monitor)

    @Test
    fun `unscheduled live date reuses authored content consistently across repository instances`() {
        assertThat(repository.findByDate(date)).isNull()
        val live = requireNotNull(repository.findForLiveDate(date))
        val source = repository.findAllDates().mapNotNull { repository.findByDate(LocalDate.parse(it)) }
            .first { it.answer == live.answer }
        assertThat(live).isEqualTo(source.copy(date = date.toString()))
        assertThat(DailyRepository(mapper).findForLiveDate(date)).isEqualTo(live)
        assertThat(repository.findByDate(LocalDate.parse(source.date))).isEqualTo(source)
        val picks = (0L..30L).map { repository.findForLiveDate(date.plusDays(it))!!.answer }.toSet()
        assertThat(picks.size).isGreaterThan(1)
    }

    @Test
    fun `explicit schedule takes precedence over fallback`() {
        val scheduled = LocalDate.of(2026, 10, 25)
        assertThat(repository.findForLiveDate(scheduled)).isEqualTo(repository.findByDate(scheduled))
        assertThat(repository.findForLiveDate(scheduled)!!.answer).isEqualTo("Tom Brady")
    }

    @Test
    fun `fallback is a real ranked daily with today's date and one attempt per account`() {
        val user = UserIdentity("live-player", "Player")
        val first = service().start(user = user)
        val other = service().start(user = UserIdentity("other-player", "Other"))
        val guest = service().start()
        assertThat(first.ranked).isTrue()
        assertThat(first.date).isEqualTo(date.toString())
        assertThat(other.hints).isEqualTo(first.hints)
        assertThat(guest.hints).isEqualTo(first.hints)
        assertThat(guest.ranked).isFalse()
        assertThat(service().start(user = user).sessionId).isEqualTo(first.sessionId)
        val answer = requireNotNull(repository.findForLiveDate(date)).answer
        val completed = service().submitAnswer(DailyAnswerRequest(first.sessionId, answer), user)
        assertThat(completed.correct).isTrue()
        assertThat(completed.submittedScore!!.date).isEqualTo(date.toString())
        assertThat(scores.leaderboard(null, null, null).scores).hasSize(1)
        assertThat(service().start(user = user).completed).isTrue()
    }

    @Test
    fun `production preview permits future and current practice without unlocking ranked date overrides`() {
        assertThat(service().availableDates()).contains("2099-01-01", "2026-10-25")
        val preview = service().start(LocalDate.of(2099, 1, 1), practice = true)
        assertThat(preview.ranked).isFalse()
        val completed = service().submitAnswer(DailyAnswerRequest(preview.sessionId, "Jordan"))
        assertThat(completed.correct).isTrue()
        assertThat(completed.submittedScore).isNull()
        assertThatThrownBy { service().submitScore(DailyScoreSubmissionRequest(preview.sessionId), UserIdentity("x", "X")) }
            .isInstanceOf(InvalidDailyRequestException::class.java)
        assertThat(service().start(practice = true).date).isEqualTo(date.toString())
        assertThatThrownBy { service().start(LocalDate.of(2099, 1, 1), UserIdentity("x", "X")) }
            .isInstanceOf(InvalidDailyRequestException::class.java)
        assertThatThrownBy { service().start(date.minusDays(10), practice = true) }
            .isInstanceOf(DailyNotFoundException::class.java)
    }

    @Test
    fun `live selection rolls over at London midnight`() {
        val summerClock = Clock.fixed(Instant.parse("2026-07-01T23:05:00Z"), ZoneOffset.UTC)
        val summerScores = DailyScoreService(results, DailyLeaderboardProperties(), summerClock)
        val daily = DailyService(repository, com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher(),
            summerScores, attempts, properties, summerClock, monitor)
        assertThat(daily.start().date).isEqualTo("2026-07-02")
    }
}
