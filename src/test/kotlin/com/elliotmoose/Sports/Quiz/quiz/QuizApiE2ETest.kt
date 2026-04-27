package com.elliotmoose.Sports.Quiz.quiz

import com.elliotmoose.Sports.Quiz.quiz.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "quiz.questions.storage=local",
        "quiz.storage=local"
    ]
)
class QuizApiE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val jsonHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    fun `create quiz returns expected api response`() {
        val response = createLogoQuiz(numberOfQuestions = 3)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull

        val body = requireNotNull(response.body)
        assertThat(body.quizId).isNotBlank()
        assertThat(body.questions).hasSize(3)
        assertThat(body.questions).allSatisfy { question ->
            assertThat(question.id).isNotBlank()
            assertThat(question.league).isEqualTo(League.MLB)
            assertThat(question.logoUrl).isNotBlank()
            assertThat(question.fullName).isNotBlank()
            assertThat(question.hints.keys).containsExactlyInAnyOrder(
                QuizDifficulty.EASY,
                QuizDifficulty.MEDIUM,
                QuizDifficulty.HARD
            )
            assertThat(question.hints.values.flatten()).allSatisfy { hint ->
                assertThat(hint).isNotBlank()
            }
        }
    }

    @Test
    fun `submit answer returns expected api response`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 1).body)
        val question = quiz.questions.single()

        val response = restTemplate.exchange(
            "http://localhost:$port/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity(
                AnswerRequest(
                    quizId = quiz.quizId,
                    questionId = question.id,
                    answer = question.fullName
                ),
                jsonHeaders
            ),
            AnswerResponse::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull

        val body = requireNotNull(response.body)
        assertThat(body.correct).isTrue()
        assertThat(body.correctAnswer).isEqualTo(question.fullName)
        assertThat(body.matchedAnswer).isNotBlank()
        assertThat(body.normalizedAnswer).isNotBlank()
        assertThat(body.attemptsRemaining).isEqualTo(1)
        assertThat(body.shouldAdvance).isTrue()
    }

    @Test
    fun `skip question returns expected api response`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 1).body)
        val question = quiz.questions.single()

        val response = restTemplate.exchange(
            "http://localhost:$port/v0/quiz/skip",
            HttpMethod.POST,
            HttpEntity(
                SkipRequest(
                    quizId = quiz.quizId,
                    questionId = question.id
                ),
                jsonHeaders
            ),
            SkipResponse::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull

        val body = requireNotNull(response.body)
        assertThat(body.skipped).isTrue()
        assertThat(body.correctAnswer).isEqualTo(question.fullName)
    }

    @Test
    fun `get quiz returns expected api response`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 2).body)
        val firstQuestion = quiz.questions[0]
        val secondQuestion = quiz.questions[1]

        restTemplate.exchange(
            "http://localhost:$port/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity(
                AnswerRequest(
                    quizId = quiz.quizId,
                    questionId = firstQuestion.id,
                    answer = firstQuestion.fullName
                ),
                jsonHeaders
            ),
            AnswerResponse::class.java
        )

        restTemplate.exchange(
            "http://localhost:$port/v0/quiz/skip",
            HttpMethod.POST,
            HttpEntity(
                SkipRequest(
                    quizId = quiz.quizId,
                    questionId = secondQuestion.id
                ),
                jsonHeaders
            ),
            SkipResponse::class.java
        )

        val response = restTemplate.getForEntity(
            "http://localhost:$port/v0/quiz/${quiz.quizId}",
            QuizReviewResponse::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull

        val body = requireNotNull(response.body)
        assertThat(body.quizId).isEqualTo(quiz.quizId)
        assertThat(body.difficulty).isEqualTo(QuizDifficulty.EASY)
        assertThat(body.totalQuestions).isEqualTo(2)
        assertThat(body.correctCount).isEqualTo(1)
        assertThat(body.skippedCount).isEqualTo(1)
        assertThat(body.incorrectCount).isEqualTo(0)
        assertThat(body.elapsedSeconds).isGreaterThanOrEqualTo(1)
        assertThat(body.score).isGreaterThanOrEqualTo(0)
        assertThat(body.questions).hasSize(2)

        val reviewedFirst = body.questions.first { it.id == firstQuestion.id }
        assertThat(reviewedFirst.fullName).isEqualTo(firstQuestion.fullName)
        assertThat(reviewedFirst.correct).isTrue()
        assertThat(reviewedFirst.skipped).isFalse()
        assertThat(reviewedFirst.submittedAnswer).isEqualTo(firstQuestion.fullName)
        assertThat(reviewedFirst.hinted).isFalse()

        val reviewedSecond = body.questions.first { it.id == secondQuestion.id }
        assertThat(reviewedSecond.fullName).isEqualTo(secondQuestion.fullName)
        assertThat(reviewedSecond.correct).isFalse()
        assertThat(reviewedSecond.skipped).isTrue()
        assertThat(reviewedSecond.submittedAnswer).isNull()
        assertThat(reviewedSecond.hinted).isFalse()
    }

    private fun createLogoQuiz(numberOfQuestions: Int): ResponseEntity<QuizResponse> {
        val request = QuizRequest(
            leagues = setOf(League.MLB),
            numberOfQuestions = numberOfQuestions,
            difficulty = QuizDifficulty.EASY,
            type = QuizType.LOGO
        )

        return restTemplate.exchange(
            "http://localhost:$port/v0/quiz",
            HttpMethod.POST,
            HttpEntity(request, jsonHeaders),
            QuizResponse::class.java
        )
    }
}
