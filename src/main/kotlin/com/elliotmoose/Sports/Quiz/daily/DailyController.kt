package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentityProvider
import com.elliotmoose.Sports.Quiz.daily.model.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v0/daily")
class DailyController(
    private val dailyService: DailyService,
    private val scores: DailyScoreService,
    private val identities: UserIdentityProvider
) {
    @GetMapping("/dates")
    fun availableDates(): List<String> = dailyService.availableDates()

    @PostMapping
    fun start(
        @RequestBody request: DailyStartRequest,
        @RequestHeader("Authorization", required = false) authorization: String?
    ): DailyStartResponse = dailyService.start(request.date?.let(scores::parseDate) ?: scores.today(), identities.resolve(authorization), request.practice)

    @GetMapping("/sessions/{sessionId}")
    fun resume(@PathVariable sessionId: String, @RequestHeader("Authorization", required = false) authorization: String?): DailyStartResponse =
        dailyService.resume(sessionId, identities.resolve(authorization))

    @PostMapping("/hint")
    fun nextHint(@RequestBody request: DailyNextHintRequest,
        @RequestHeader("Authorization", required = false) authorization: String?): DailyNextHintResponse =
        dailyService.nextHint(request, identities.resolve(authorization))

    @PostMapping("/answer")
    fun submitAnswer(@RequestBody request: DailyAnswerRequest,
        @RequestHeader("Authorization", required = false) authorization: String?): DailyAnswerResponse =
        dailyService.submitAnswer(request, identities.resolve(authorization))

    @PostMapping("/give-up")
    fun giveUp(@RequestBody request: DailyGiveUpRequest,
        @RequestHeader("Authorization", required = false) authorization: String?): DailyGiveUpResponse =
        dailyService.giveUp(request, identities.resolve(authorization))

    @PostMapping("/scores")
    fun submitScore(@RequestBody request: DailyScoreSubmissionRequest,
        @RequestHeader("Authorization", required = false) authorization: String?): DailyScoreResponse =
        dailyService.submitScore(request, identities.resolve(authorization) ?: throw AuthenticationRequiredException())

    @GetMapping("/leaderboard")
    fun leaderboard(@RequestParam(required = false) date: String?, @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) cursor: String?): DailyScorePageResponse =
        DailyScorePageResponse.from(scores.leaderboard(date, limit, cursor))

    @GetMapping("/history")
    fun history(@RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(required = false) limit: Int?, @RequestParam(required = false) cursor: String?): DailyScorePageResponse =
        DailyScorePageResponse.from(scores.history(identities.resolve(authorization) ?: throw AuthenticationRequiredException(), limit, cursor))

    @GetMapping("/scores/me")
    fun myScore(@RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam date: String): DailyScoreResponse? =
        scores.scoreForDay(identities.resolve(authorization) ?: throw AuthenticationRequiredException(), date)?.let(DailyScoreResponse::from)
}
