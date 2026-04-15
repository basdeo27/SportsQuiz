package com.elliotmoose.Sports.Quiz.quiz.difficulty

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DifficultyServiceTest {

    private val matcher = QuizAnswerMatcher()
    private val service = DifficultyService(
        listOf(
            EasyDifficultyHandler(matcher),
            MediumDifficultyHandler(matcher),
            HardDifficultyHandler(matcher)
        )
    )

    @Test
    fun `easy difficulty allows another attempt after first wrong answer`() {
        val result = service.evaluateAnswer(
            difficulty = QuizDifficulty.EASY,
            question = question(fullName = "Arizona Diamondbacks", correctAnswers = setOf("Diamondbacks")),
            submittedAnswer = "wrong answer",
            attemptsUsed = 1
        )

        assertThat(result.matchedAnswer).isNull()
        assertThat(result.attemptsRemaining).isEqualTo(1)
        assertThat(result.shouldAdvance).isFalse()
    }

    @Test
    fun `easy difficulty advances after second wrong answer`() {
        val result = service.evaluateAnswer(
            difficulty = QuizDifficulty.EASY,
            question = question(fullName = "Arizona Diamondbacks", correctAnswers = setOf("Diamondbacks")),
            submittedAnswer = "wrong answer",
            attemptsUsed = 2
        )

        assertThat(result.matchedAnswer).isNull()
        assertThat(result.attemptsRemaining).isEqualTo(0)
        assertThat(result.shouldAdvance).isTrue()
    }

    @Test
    fun `medium difficulty advances immediately and matches exact normalized answer`() {
        val result = service.evaluateAnswer(
            difficulty = QuizDifficulty.MEDIUM,
            question = question(fullName = "Boston Red Sox", correctAnswers = setOf("Red Sox")),
            submittedAnswer = "red sox",
            attemptsUsed = 1
        )

        assertThat(result.matchedAnswer).isEqualTo("Red Sox")
        assertThat(result.attemptsRemaining).isEqualTo(0)
        assertThat(result.shouldAdvance).isTrue()
    }

    @Test
    fun `hard difficulty matches only full name and advances immediately`() {
        val result = service.evaluateAnswer(
            difficulty = QuizDifficulty.HARD,
            question = question(fullName = "Boston Red Sox", correctAnswers = setOf("Red Sox")),
            submittedAnswer = "Boston Red Sox",
            attemptsUsed = 1
        )

        assertThat(result.matchedAnswer).isEqualTo("Boston Red Sox")
        assertThat(result.attemptsRemaining).isEqualTo(0)
        assertThat(result.shouldAdvance).isTrue()
    }

    private fun question(fullName: String, correctAnswers: Set<String>): Question {
        return Question(
            id = "question-1",
            league = League.MLB,
            logoUrl = "https://example.com/logo.png",
            fullName = fullName,
            correctAnswers = correctAnswers
        )
    }
}
