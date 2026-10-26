package com.elliotmoose.Sports.Quiz.daily.model

data class DailyStartResponse(
    val sessionId: String,
    val date: String,
    val hints: List<DailyHint>,
    val hintsRevealed: Int,
    val totalHints: Int,
    val estimatedScore: Int,
    val timePenaltyPerSecond: Int = DailyScoring.TIME_PENALTY_PER_SECOND,
    val hintPenaltyMultiplier: Int = DailyScoring.HINT_PENALTY_MULTIPLIER,
    val ranked: Boolean = true,
    val completed: Boolean = false,
    val gaveUp: Boolean = false,
    val finalScore: Int? = null,
    val submittedScore: DailyScoreResponse? = null,
    val elapsedSeconds: Long = 0
)

data class DailyNextHintResponse(
    val hints: List<DailyHint>,
    val hintsRevealed: Int,
    val totalHints: Int,
    val estimatedScore: Int
)

data class DailyAnswerResponse(
    val correct: Boolean,
    val score: Int?,
    val elapsedSeconds: Long,
    val submittedScore: DailyScoreResponse? = null
)

data class DailyGiveUpResponse(
    val message: String = "Better luck tomorrow!",
    val submittedScore: DailyScoreResponse? = null,
    val elapsedSeconds: Long = 0
)
