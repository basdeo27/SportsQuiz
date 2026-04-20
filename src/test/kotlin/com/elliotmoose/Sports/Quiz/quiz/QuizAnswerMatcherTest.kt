package com.elliotmoose.Sports.Quiz.quiz

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QuizAnswerMatcherTest {

    private val matcher = QuizAnswerMatcher()

    @Test
    fun `normalize removes punctuation and collapses whitespace`() {
        val normalized = matcher.normalize("  St.  Louis!!   Cardinals  ")

        assertThat(normalized).isEqualTo("st louis cardinals")
    }

    @Test
    fun `easy difficulty matches close typo by similarity`() {
        val matchedAnswer = matcher.findEasyMatch(
            correctAnswers = setOf("Diamondbacks"),
            submittedAnswer = "Diamonbacks"
        )

        assertThat(matchedAnswer).isEqualTo("Diamondbacks")
    }

    @Test
    fun `easy difficulty matches on strong token overlap`() {
        val matchedAnswer = matcher.findEasyMatch(
            correctAnswers = setOf("New York City FC"),
            submittedAnswer = "new york fc"
        )

        assertThat(matchedAnswer).isEqualTo("New York City FC")
    }

    @Test
    fun `medium difficulty requires exact normalized candidate`() {
        val exactMatch = matcher.findExactNormalizedMatch(
            correctAnswers = setOf("Red Sox"),
            submittedAnswer = "red sox"
        )
        val nearMatch = matcher.findExactNormalizedMatch(
            correctAnswers = setOf("Red Sox"),
            submittedAnswer = "red soxx"
        )

        assertThat(exactMatch).isEqualTo("Red Sox")
        assertThat(nearMatch).isNull()
    }

    @Test
    fun `hard difficulty requires exact full name`() {
        assertThat(matcher.isExactFullNameMatch("Boston Red Sox", "Red Sox")).isFalse()
        assertThat(matcher.isExactFullNameMatch("Boston Red Sox", "Boston Red Sox")).isTrue()
    }
}
