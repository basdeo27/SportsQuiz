package com.elliotmoose.Sports.Quiz.quiz

import org.springframework.stereotype.Component
import kotlin.math.max

@Component
class QuizAnswerMatcher {

    fun findEasyMatch(
        correctAnswers: Set<String>,
        submittedAnswer: String
    ): String? {
        val normalizedAnswer = normalize(submittedAnswer)
        return correctAnswers.firstOrNull { candidate ->
            isEasyMatch(normalizedAnswer, candidate)
        }
    }

    fun findExactNormalizedMatch(
        correctAnswers: Set<String>,
        submittedAnswer: String
    ): String? {
        val normalizedAnswer = normalize(submittedAnswer)
        return correctAnswers.firstOrNull { candidate ->
            normalize(candidate) == normalizedAnswer
        }
    }

    fun isExactFullNameMatch(
        fullName: String,
        submittedAnswer: String
    ): Boolean {
        return normalize(fullName) == normalize(submittedAnswer)
    }

    fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isEasyMatch(normalizedAnswer: String, candidate: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate == normalizedAnswer) {
            return true
        }

        val similarity = similarityScore(normalizedAnswer, normalizedCandidate)
        if (similarity >= EASY_MATCH_THRESHOLD) {
            return true
        }

        val answerTokens = normalizedAnswer.split(" ").filter { it.isNotBlank() }
        val candidateTokens = normalizedCandidate.split(" ").filter { it.isNotBlank() }
        if (candidateTokens.isNotEmpty()) {
            val matchingTokens = candidateTokens.count { token -> answerTokens.contains(token) }
            val tokenMatchRatio = matchingTokens.toDouble() / candidateTokens.size
            if (tokenMatchRatio >= TOKEN_MATCH_THRESHOLD) {
                return true
            }
        }

        return false
    }

    private fun similarityScore(a: String, b: String): Double {
        val maxLength = max(a.length, b.length)
        if (maxLength == 0) {
            return 1.0
        }
        val distance = levenshteinDistance(a, b)
        return 1.0 - distance.toDouble() / maxLength
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) {
            dp[i][0] = i
        }
        for (j in 0..b.length) {
            dp[0][j] = j
        }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    companion object {
        private const val EASY_MATCH_THRESHOLD = 0.8
        private const val TOKEN_MATCH_THRESHOLD = 0.75
    }
}
