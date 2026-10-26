package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import com.elliotmoose.Sports.Quiz.common.monitoring.UsageMonitor
import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.daily.model.*
import com.elliotmoose.Sports.Quiz.daily.repository.DailyAttemptRepository
import com.elliotmoose.Sports.Quiz.daily.properties.DailyAttemptProperties
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
@EnableConfigurationProperties(DailyAttemptProperties::class)
class DailyService(
    private val repository: DailyRepository,
    private val answerMatcher: QuizAnswerMatcher,
    private val scores: DailyScoreService,
    private val attempts: DailyAttemptRepository,
    private val properties: DailyAttemptProperties,
    private val clock: Clock,
    private val monitoring: UsageMonitor,
    private val media: DailyMediaResolver = DailyMediaResolver()
) {
    fun availableDates(): List<String> = repository.findAllDates().filter {
        properties.allowDateOverride || properties.allowPracticeDateOverride || it < scores.today().toString()
    }

    fun start(date: LocalDate = scores.today(), user: UserIdentity? = null, practice: Boolean = false): DailyStartResponse {
        if (!practice && user == null && properties.loginRequired) throw AuthenticationRequiredException()
        val today = scores.today()
        val allowOverride = properties.allowDateOverride || (practice && properties.allowPracticeDateOverride)
        if (!allowOverride && ((practice && date >= today) || (!practice && date != today))) {
            throw InvalidDailyRequestException(if (practice) "Practice is available for past challenges only." else "Ranked play is available for today's challenge only.")
        }
        val entry = (if (date == today) repository.findForLiveDate(date) else repository.findByDate(date))
            ?: throw DailyNotFoundException("No daily challenge is available for $date. Check back later!")
        val ranked = !practice && user != null
        val candidate = DailySession(
            id = if (ranked) "RANKED#${user!!.id}#$date" else UUID.randomUUID().toString(),
            date = date.toString(), entry = entry, user = if (ranked) user else null,
            startTimeMillis = clock.millis(), ranked = ranked
        )
        // Existing pre-persistence scores must never get a second ranked attempt.
        val previous = if (ranked) scores.scoreForDay(user!!, date.toString()) else null
        val initial = if (previous == null) candidate else candidate.copy(completed = true, gaveUp = previous.gaveUp,
            hintsRevealed = previous.hintsRevealed, startTimeMillis = previous.completedAtMillis - previous.elapsedSeconds * 1000,
            completedAtMillis = previous.completedAtMillis, finalScore = previous.score, submittedScore = previous)
        val session = persistCompleted(attempts.createIfAbsent(initial), user)
        return response(session)
    }

    fun resume(id: String, user: UserIdentity?): DailyStartResponse = response(persistCompleted(resolve(id, user), user))

    fun nextHint(request: DailyNextHintRequest, user: UserIdentity? = null): DailyNextHintResponse {
        val updated = mutate(request.sessionId, user) { session ->
            if (session.completed) throw DailySessionCompletedException("This session has already ended.")
            if (session.hintsRevealed >= session.entry.hints.size) throw NoMoreHintsException("All hints have already been revealed.")
            session.copy(hintsRevealed = session.hintsRevealed + 1)
        }
        return DailyNextHintResponse(updated.entry.hints.take(updated.hintsRevealed).map(media::resolve), updated.hintsRevealed,
            updated.entry.hints.size, estimate(updated))
    }

    fun submitAnswer(request: DailyAnswerRequest, user: UserIdentity? = null): DailyAnswerResponse {
        val updated = mutate(request.sessionId, user) { session ->
            if (session.completed) {
                if (session.gaveUp) throw DailySessionCompletedException("This session has already ended.")
                session
            } else if (answerMatcher.findEasyMatch((session.entry.answers + session.entry.answer).toSet(), request.answer) != null) {
                session.copy(completed = true, finalScore = estimate(session), completedAtMillis = clock.millis())
            } else session
        }
        val saved = persistCompleted(updated, user)
        return DailyAnswerResponse(saved.completed, saved.finalScore,
            ((saved.completedAtMillis ?: clock.millis()) - saved.startTimeMillis).coerceAtLeast(0) / 1000,
            saved.submittedScore?.let(DailyScoreResponse::from))
    }

    fun giveUp(request: DailyGiveUpRequest, user: UserIdentity? = null): DailyGiveUpResponse {
        val updated = mutate(request.sessionId, user) { session ->
            if (session.completed) session else session.copy(completed = true, gaveUp = true, finalScore = 0, completedAtMillis = clock.millis())
        }
        val saved = persistCompleted(updated, user)
        return DailyGiveUpResponse(submittedScore = saved.submittedScore?.let(DailyScoreResponse::from),
            elapsedSeconds = ((saved.completedAtMillis ?: clock.millis()) - saved.startTimeMillis).coerceAtLeast(0) / 1000)
    }

    fun submitScore(request: DailyScoreSubmissionRequest, user: UserIdentity): DailyScoreResponse {
        val session = resolve(request.sessionId, user)
        if (!session.ranked) throw InvalidDailyRequestException("Unranked scores cannot enter the leaderboard. Sign in before starting a ranked attempt.")
        if (!session.completed) throw InvalidDailyRequestException("Complete the challenge before submitting a score.")
        return DailyScoreResponse.from(requireNotNull(persistCompleted(session, user).submittedScore))
    }

    private fun persistCompleted(session: DailySession, user: UserIdentity?): DailySession {
        if (!session.completed) return session
        if (!session.ranked) {
            monitoring.quizCompleted("DAILY_PRACTICE#${session.id}", CompletionType.DAILY_PRACTICE, requireNotNull(session.completedAtMillis))
            return session
        }
        if (session.submittedScore != null) {
            if (session.submittedScore.sessionId == session.id) monitoring.quizCompleted("DAILY_RANKED#${session.id}", CompletionType.DAILY_RANKED, requireNotNull(session.completedAtMillis))
            return session
        }
        val identity = user ?: throw AuthenticationRequiredException()
        val score = scores.record(session, identity)
        monitoring.quizCompleted("DAILY_RANKED#${session.id}", CompletionType.DAILY_RANKED, requireNotNull(session.completedAtMillis))
        val updated = session.copy(submittedScore = score, version = session.version + 1)
        // If a concurrent writer won, its completion is canonical. A retry can finish the idempotent score write.
        return if (attempts.replace(session.version, updated)) updated else resolve(session.id, user).copy(submittedScore = score)
    }

    private fun mutate(id: String, user: UserIdentity?, update: (DailySession) -> DailySession): DailySession {
        repeat(12) {
            val session = resolve(id, user)
            val changed = update(session)
            if (changed == session) return session
            val updated = changed.copy(version = session.version + 1)
            if (attempts.replace(session.version, updated)) return updated
        }
        throw DailyScoreConflictException("This attempt changed in another tab. Please reload and try again.")
    }

    private fun resolve(id: String, user: UserIdentity?): DailySession {
        val session = attempts.find(id) ?: throw DailySessionNotFoundException("Challenge session not found.")
        if (session.ranked && (user == null || session.user?.id != user.id)) throw AuthenticationRequiredException()
        if (!session.ranked && clock.millis() - session.startTimeMillis >= 86400000) {
            throw DailySessionNotFoundException("This unranked session has expired.")
        }
        return session
    }

    private fun estimate(session: DailySession): Int = session.finalScore
        ?: DailyScoring.calculate(session.hintsRevealed, session.startTimeMillis, clock.millis())

    private fun response(session: DailySession) = DailyStartResponse(
        session.id, session.date, session.entry.hints.take(session.hintsRevealed).map(media::resolve), session.hintsRevealed,
        session.entry.hints.size, estimate(session), ranked = session.ranked, completed = session.completed,
        gaveUp = session.gaveUp, finalScore = session.finalScore, submittedScore = session.submittedScore?.let(DailyScoreResponse::from),
        elapsedSeconds = ((session.completedAtMillis ?: clock.millis()) - session.startTimeMillis).coerceAtLeast(0) / 1000
    )
}
