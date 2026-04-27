package com.elliotmoose.Sports.Quiz.quiz

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

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

    // -------------------------------------------------------------------------
    // Parameterized easy-match acceptance/rejection tests
    // -------------------------------------------------------------------------

    data class EasyMatchCase(
        val description: String,
        val correctAnswers: Set<String>,
        val submitted: String,
        val shouldMatch: Boolean
    ) {
        override fun toString() = description
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("easyMatchCases")
    fun `easy match behaviour`(case: EasyMatchCase) {
        val result = matcher.findEasyMatch(
            correctAnswers = case.correctAnswers,
            submittedAnswer = case.submitted
        )
        if (case.shouldMatch) {
            assertThat(result).`as`("expected a match for: '${case.submitted}'").isNotNull()
        } else {
            assertThat(result).`as`("expected no match for: '${case.submitted}'").isNull()
        }
    }

    companion object {

        private val DIAMONDBACKS_ANSWERS = setOf("Diamondbacks", "D-backs", "Dbacks", "Arizona Diamondbacks")
        private val RED_SOX_ANSWERS = setOf("Red Sox", "Boston Red Sox")
        private val CUBS_ANSWERS = setOf("Cubs", "Chicago Cubs")

        @JvmStatic
        fun easyMatchCases(): Stream<EasyMatchCase> = Stream.of(
            // --- exact nickname ---
            EasyMatchCase("exact nickname", DIAMONDBACKS_ANSWERS, "Diamondbacks", shouldMatch = true),
            EasyMatchCase("exact nickname lowercase", DIAMONDBACKS_ANSWERS, "diamondbacks", shouldMatch = true),
            EasyMatchCase("exact nickname uppercase", DIAMONDBACKS_ANSWERS, "DIAMONDBACKS", shouldMatch = true),

            // --- exact full name (city + nickname) ---
            EasyMatchCase("exact full name", DIAMONDBACKS_ANSWERS, "Arizona Diamondbacks", shouldMatch = true),
            EasyMatchCase("exact full name lowercase", DIAMONDBACKS_ANSWERS, "arizona diamondbacks", shouldMatch = true),
            EasyMatchCase("full name extra internal spaces", DIAMONDBACKS_ANSWERS, "Arizona  Diamondbacks", shouldMatch = true),

            // --- alternate accepted nicknames ---
            EasyMatchCase("alternate answer D-backs", DIAMONDBACKS_ANSWERS, "D-backs", shouldMatch = true),
            EasyMatchCase("alternate answer Dbacks", DIAMONDBACKS_ANSWERS, "Dbacks", shouldMatch = true),

            // --- typos on nickname (easy is forgiving) ---
            EasyMatchCase("one-letter typo in nickname", DIAMONDBACKS_ANSWERS, "Diamonbacks", shouldMatch = true),
            EasyMatchCase("one-letter substitution in nickname", DIAMONDBACKS_ANSWERS, "Diamondbackz", shouldMatch = true),

            // --- typos on full name ---
            EasyMatchCase("one-letter typo in city", DIAMONDBACKS_ANSWERS, "Arizone Diamondbacks", shouldMatch = true),

            // --- other teams: exact nickname and full name ---
            EasyMatchCase("Red Sox nickname", RED_SOX_ANSWERS, "Red Sox", shouldMatch = true),
            EasyMatchCase("Red Sox full name", RED_SOX_ANSWERS, "Boston Red Sox", shouldMatch = true),

            // ---- INVALID: answer merely contains the nickname (extra words) ----
            EasyMatchCase(
                "extra words before nickname",
                DIAMONDBACKS_ANSWERS, "I think it's the Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "extra words after nickname",
                DIAMONDBACKS_ANSWERS, "Diamondbacks baseball", shouldMatch = false
            ),
            EasyMatchCase(
                "extra words after full name",
                DIAMONDBACKS_ANSWERS, "Arizona Diamondbacks baseball", shouldMatch = false
            ),
            EasyMatchCase(
                "leading 'The' before nickname",
                DIAMONDBACKS_ANSWERS, "The Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "leading 'The' before full name",
                DIAMONDBACKS_ANSWERS, "The Arizona Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "sentence fragment containing nickname",
                DIAMONDBACKS_ANSWERS, "My answer is Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "sentence fragment containing full name",
                DIAMONDBACKS_ANSWERS, "I guess Arizona Diamondbacks", shouldMatch = false
            ),

            // ---- INVALID: wrong city with correct nickname ----
            EasyMatchCase(
                "wrong city Chicago before Diamondbacks",
                DIAMONDBACKS_ANSWERS, "Chicago Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "wrong city New York before Diamondbacks",
                DIAMONDBACKS_ANSWERS, "New York Diamondbacks", shouldMatch = false
            ),
            EasyMatchCase(
                "wrong city Boston before nickname",
                DIAMONDBACKS_ANSWERS, "Boston Diamondbacks", shouldMatch = false
            ),

            // ---- INVALID: wrong city with correct nickname (other teams) ----
            EasyMatchCase(
                "wrong city Arizona before Red Sox",
                RED_SOX_ANSWERS, "Arizona Red Sox", shouldMatch = false
            ),
            EasyMatchCase(
                "wrong city Chicago before Red Sox",
                RED_SOX_ANSWERS, "Chicago Red Sox", shouldMatch = false
            ),

            // ---- INVALID: just the city alone ----
            EasyMatchCase("just the city", DIAMONDBACKS_ANSWERS, "Arizona", shouldMatch = false),
            EasyMatchCase("just the city for Red Sox", RED_SOX_ANSWERS, "Boston", shouldMatch = false),

            // ---- INVALID: completely wrong answer ----
            EasyMatchCase("completely wrong team", DIAMONDBACKS_ANSWERS, "Red Sox", shouldMatch = false),
            EasyMatchCase("completely wrong team 2", DIAMONDBACKS_ANSWERS, "Cardinals", shouldMatch = false),
            EasyMatchCase("blank answer", DIAMONDBACKS_ANSWERS, "", shouldMatch = false),

            // ---- Edge: multi-word nickname where one word is dropped ----
            EasyMatchCase("Red Sox nickname exact", RED_SOX_ANSWERS, "Red Sox", shouldMatch = true),
            EasyMatchCase("Red Sox with extra adjective", RED_SOX_ANSWERS, "Great Red Sox", shouldMatch = false),
            EasyMatchCase("Cubs extra word", CUBS_ANSWERS, "Cubs team", shouldMatch = false),
            EasyMatchCase("Cubs correct", CUBS_ANSWERS, "Cubs", shouldMatch = true),
            EasyMatchCase("Chicago Cubs correct", CUBS_ANSWERS, "Chicago Cubs", shouldMatch = true)
        )
    }
}
