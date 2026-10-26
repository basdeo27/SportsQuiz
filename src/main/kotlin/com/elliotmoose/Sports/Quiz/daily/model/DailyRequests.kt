package com.elliotmoose.Sports.Quiz.daily.model

data class DailyNextHintRequest(val sessionId: String)
data class DailyAnswerRequest(val sessionId: String, val answer: String)
data class DailyGiveUpRequest(val sessionId: String)
data class DailyScoreSubmissionRequest(val sessionId: String)

data class DailyStartRequest(val date: String? = null, val practice: Boolean = false)
