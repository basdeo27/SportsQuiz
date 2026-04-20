package com.elliotmoose.Sports.Quiz.quiz.model

data class QuizSummary(
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val elapsedSeconds: Long,
    val score: Int
)

object QuizScoring {
    fun summarize(quiz: Quiz): QuizSummary {
        val totalQuestions = quiz.questions.size
        val correctCount = quiz.questions.count { it.isCorrect == true }
        val skippedCount = quiz.questions.count { it.isSkipped == true }
        val incorrectCount = totalQuestions - correctCount - skippedCount
        val completedAtMillis = quiz.completedAtMillis ?: System.currentTimeMillis()
        val elapsedSeconds = maxOf(1L, (completedAtMillis - quiz.startedAtMillis) / 1000)
        val score = calculateScore(
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            elapsedSeconds = elapsedSeconds
        )
        return QuizSummary(
            totalQuestions = totalQuestions,
            correctCount = correctCount,
            incorrectCount = incorrectCount,
            skippedCount = skippedCount,
            elapsedSeconds = elapsedSeconds,
            score = score
        )
    }

    private fun calculateScore(
        correctCount: Int,
        totalQuestions: Int,
        elapsedSeconds: Long
    ): Int {
        if (totalQuestions == 0) {
            return 0
        }
        val accuracy = correctCount.toDouble() / totalQuestions.toDouble()
        val pace = totalQuestions.toDouble() / elapsedSeconds.toDouble()
        val rawScore = (1000.0 + (totalQuestions * 10.0) + (pace * 1000.0)) * accuracy
        return rawScore.toInt().coerceAtLeast(0)
    }
}
