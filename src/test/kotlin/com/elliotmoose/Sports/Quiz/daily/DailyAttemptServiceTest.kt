package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.daily.model.*
import com.elliotmoose.Sports.Quiz.daily.properties.*
import com.elliotmoose.Sports.Quiz.daily.repository.*
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.*
import com.elliotmoose.Sports.Quiz.monitoring.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class DailyAttemptServiceTest {
    private class MutableClock(var now: Instant = Instant.parse("2099-01-02T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
        override fun instant(): Instant = now
    }
    private val clock = MutableClock()
    private val attempts = LocalDailyAttemptRepository()
    private val results = LocalDailyScoreRepository()
    private val scores = DailyScoreService(results, DailyLeaderboardProperties(), clock)
    private val user = UserIdentity("one", "Player")
    private fun service(overrideDates: Boolean = false, loginRequired: Boolean = false) = DailyService(DailyRepository(jacksonObjectMapper()),
        QuizAnswerMatcher(), scores, attempts, DailyAttemptProperties(allowDateOverride = overrideDates, loginRequired = loginRequired), clock, MonitoringService(LocalMonitoringRepository(), MonitoringProperties(), clock))

    @Test
    fun `guests can play today by default but cannot claim an unranked result after signing in`() {
        clock.now = Instant.parse("2026-10-25T12:00:00Z")
        val service = service()
        val guest = service.start()
        assertThat(guest.ranked).isFalse()
        assertThat(guest.sessionId).doesNotStartWith("RANKED#")
        service.nextHint(DailyNextHintRequest(guest.sessionId))
        clock.now = clock.now.plusSeconds(15)
        val result = service.submitAnswer(DailyAnswerRequest(guest.sessionId, "Brady"))
        assertThat(result.correct).isTrue()
        assertThat(result.submittedScore).isNull()
        assertThat(result.elapsedSeconds).isEqualTo(15)
        clock.now = clock.now.plusSeconds(10)
        val resumed = service.resume(guest.sessionId, null)
        assertThat(resumed.completed).isTrue()
        assertThat(resumed.finalScore).isEqualTo(result.score)
        assertThat(resumed.elapsedSeconds).isEqualTo(15)
        assertThatThrownBy { service.submitScore(DailyScoreSubmissionRequest(guest.sessionId), user) }
            .isInstanceOf(InvalidDailyRequestException::class.java)
        assertThat(scores.history(user, null, null).scores).isEmpty()

        val ranked = service.start(user = user)
        assertThat(ranked.ranked).isTrue()
        assertThat(service.start(user = user).sessionId).isEqualTo(ranked.sessionId)
    }

    @Test
    fun `guest play keeps archive restrictions expiry and give up behavior`() {
        val service = service()
        assertThatThrownBy { service.start(LocalDate.of(2099, 1, 1)) }.isInstanceOf(InvalidDailyRequestException::class.java)
        assertThatThrownBy { service.start(LocalDate.of(2099, 1, 3)) }.isInstanceOf(InvalidDailyRequestException::class.java)
        val guest = service.start()
        val result = service.giveUp(DailyGiveUpRequest(guest.sessionId))
        assertThat(result.submittedScore).isNull()
        assertThat(service.resume(guest.sessionId, null).gaveUp).isTrue()
        clock.now = clock.now.plusSeconds(86400)
        assertThatThrownBy { service.resume(guest.sessionId, null) }.isInstanceOf(DailySessionNotFoundException::class.java)
    }

    @Test
    fun `completed practice share time is frozen after answering or giving up and resuming`() {
        val service = service(true)
        val first = service.start(LocalDate.of(2026, 10, 25), practice = true)
        clock.now = clock.now.plusSeconds(84)

        val answered = service.submitAnswer(DailyAnswerRequest(first.sessionId, "Brady"))
        assertThat(answered.elapsedSeconds).isEqualTo(84)
        clock.now = clock.now.plusSeconds(600)
        assertThat(service.resume(first.sessionId, null).elapsedSeconds).isEqualTo(84)

        val second = service.start(LocalDate.of(2026, 10, 25), practice = true)
        clock.now = clock.now.plusSeconds(17)
        assertThat(service.giveUp(DailyGiveUpRequest(second.sessionId)).elapsedSeconds).isEqualTo(17)
        clock.now = clock.now.plusSeconds(600)
        assertThat(service.resume(second.sessionId, null).elapsedSeconds).isEqualTo(17)
        assertThat(service.giveUp(DailyGiveUpRequest(second.sessionId)).elapsedSeconds).isEqualTo(17)
    }

    @Test
    fun `production requires login for ranked today and restricts anonymous practice to archives`() {
        val service = service(loginRequired = true)

        assertThatThrownBy { service.start(user = null) }.isInstanceOf(AuthenticationRequiredException::class.java)
        assertThatThrownBy { service.start(LocalDate.of(2099, 1, 1), user) }.isInstanceOf(InvalidDailyRequestException::class.java)
        assertThatThrownBy { service.start(LocalDate.of(2099, 1, 3), user) }.isInstanceOf(InvalidDailyRequestException::class.java)
        assertThatThrownBy { service.start(practice = true) }.isInstanceOf(InvalidDailyRequestException::class.java)
        assertThatThrownBy { service.start(LocalDate.of(2099, 1, 3), practice = true) }.isInstanceOf(InvalidDailyRequestException::class.java)
        val practice = service.start(LocalDate.of(2099, 1, 1), practice = true)
        assertThat(practice.ranked).isFalse()
        assertThat(service.availableDates()).doesNotContain("2099-01-02")
    }

    @Test
    fun `attempt resumes across service recreation without resetting timer or hints`() {
        val first = service(true).start(LocalDate.of(2099, 1, 1), user)
        service(true).nextHint(DailyNextHintRequest(first.sessionId), user)
        clock.now = clock.now.plusSeconds(10)

        val resumed = service(true).start(LocalDate.of(2099, 1, 1), user)

        assertThat(resumed.sessionId).isEqualTo(first.sessionId)
        assertThat(resumed.hintsRevealed).isEqualTo(2)
        assertThat(resumed.estimatedScore).isEqualTo(965)
        val completed = service(true).submitAnswer(DailyAnswerRequest(first.sessionId, "Jordan"), user)
        assertThat(completed.score).isEqualTo(965)
        assertThat(service(true).start(LocalDate.of(2099, 1, 1), user).completed).isTrue()
        assertThat(scores.history(user, null, null).scores).hasSize(1)
    }

    @Test
    fun `concurrent starts across instances yield one canonical attempt and completion`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val starts = pool.invokeAll((1..12).map { Callable { service().start(user = user) } }).map { it.get() }
            assertThat(starts.map { it.sessionId }.distinct()).hasSize(1)
            val id = starts.first().sessionId
            val completions = pool.invokeAll((1..12).map { Callable {
                service().submitAnswer(DailyAnswerRequest(id, "Serena"), user)
            } }).map { it.get() }
            assertThat(completions.map { it.score }.distinct()).hasSize(1)
            assertThat(scores.history(user, null, null).scores).hasSize(1)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `practice cannot become ranked and another user cannot read or mutate an owned attempt`() {
        val service = service(true)
        val practice = service.start(LocalDate.of(2099, 1, 1), practice = true)
        service.submitAnswer(DailyAnswerRequest(practice.sessionId, "Jordan"))
        assertThatThrownBy { service.submitScore(DailyScoreSubmissionRequest(practice.sessionId), user) }
            .isInstanceOf(InvalidDailyRequestException::class.java)
        val ranked = service.start(user = user)
        assertThatThrownBy { service.resume(ranked.sessionId, null) }.isInstanceOf(AuthenticationRequiredException::class.java)
        assertThatThrownBy { service.giveUp(DailyGiveUpRequest(ranked.sessionId), UserIdentity("other", "Other")) }
            .isInstanceOf(AuthenticationRequiredException::class.java)
    }

    @Test
    fun `legacy saved results remain final and interrupted result saves can be retried`() {
        val previous = DailyScore("USER#one", "2099-01-02", "legacy", "Player", "one", 500, 120, 1, clock.millis(), false)
        results.saveFirst(previous)

        val resumed = service().start(user = user)

        assertThat(resumed.completed).isTrue()
        assertThat(resumed.finalScore).isEqualTo(500)
        assertThat(service().submitAnswer(DailyAnswerRequest(resumed.sessionId, "Serena"), user).score).isEqualTo(500)
        assertThat(scores.history(user, null, null).scores).containsExactly(previous)
    }
}
