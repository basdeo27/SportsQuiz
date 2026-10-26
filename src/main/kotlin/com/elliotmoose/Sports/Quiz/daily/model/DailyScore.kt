package com.elliotmoose.Sports.Quiz.daily.model

data class DailyScore(
    val participantId: String,
    val date: String,
    val sessionId: String,
    val displayName: String,
    val accountId: String?,
    val score: Int,
    val elapsedSeconds: Long,
    val hintsRevealed: Int,
    val completedAtMillis: Long,
    val gaveUp: Boolean
) {
    // Ascending index order: highest score first, then earliest completion, then participant.
    @get:com.fasterxml.jackson.annotation.JsonIgnore
    val rankingKey: String get() = "%04d#%013d#%s".format(1000 - score, completedAtMillis, participantId)
}

data class DailyScorePage(val scores: List<DailyScore>, val nextCursor: String?)

data class DailyScoreResponse(
    val date: String,
    val displayName: String,
    val registered: Boolean,
    val score: Int,
    val elapsedSeconds: Long,
    val hintsRevealed: Int,
    val completedAtMillis: Long,
    val gaveUp: Boolean
) {
    companion object {
        fun from(score: DailyScore) = DailyScoreResponse(
            score.date, score.displayName, score.accountId != null, score.score,
            score.elapsedSeconds, score.hintsRevealed, score.completedAtMillis, score.gaveUp
        )
    }
}

data class DailyScorePageResponse(val scores: List<DailyScoreResponse>, val nextCursor: String?) {
    companion object {
        fun from(page: DailyScorePage) = DailyScorePageResponse(page.scores.map(DailyScoreResponse::from), page.nextCursor)
    }
}
