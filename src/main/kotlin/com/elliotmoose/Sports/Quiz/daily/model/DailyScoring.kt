package com.elliotmoose.Sports.Quiz.daily.model

object DailyScoring {
    const val BASE_SCORE = 1000
    const val TIME_PENALTY_PER_SECOND = 1
    const val HINT_PENALTY_MULTIPLIER = 25

    fun calculate(hintsRevealed: Int, startTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        val elapsedSeconds = (nowMillis - startTimeMillis).coerceAtLeast(0) / 1000
        val timePenalty = elapsedSeconds * TIME_PENALTY_PER_SECOND
        // Parabolic: each extra hint beyond the first costs MULTIPLIER * n^2 where n is 1-indexed.
        // hintsRevealed=1 → 0 penalty, hintsRevealed=2 → 25, hintsRevealed=3 → 125, etc.
        val hintPenalty = (1 until hintsRevealed).sumOf { n -> HINT_PENALTY_MULTIPLIER.toLong() * n * n }
        return maxOf(0L, BASE_SCORE - timePenalty - hintPenalty).toInt()
    }
}
