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
    fun `EPL is selectable and creates a quiz using the uploaded logo pool`() {
        val leagues = restTemplate.getForEntity("http://localhost:$port/v0/quiz/leagues", Map::class.java)
        assertThat(leagues.statusCode).isEqualTo(HttpStatus.OK)
        assertThat((leagues.body?.get("LOGO") as? List<*>)?.contains("EPL")).isTrue()

        val request = QuizRequest(
            leagues = setOf(League.EPL),
            numberOfQuestions = 20,
            difficulty = QuizDifficulty.EASY,
            type = QuizType.LOGO
        )
        val response = restTemplate.exchange(
            "http://localhost:$port/v0/quiz", HttpMethod.POST,
            HttpEntity(request, jsonHeaders), QuizResponse::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val questions = requireNotNull(response.body).questions
        assertThat(questions).hasSize(20)
        assertThat(questions.map { it.fullName }).contains(
            "Fulham", "Coventry City", "Hull City", "Ipswich Town",
            "AFC Bournemouth", "Newcastle United", "Sunderland"
        )
        assertThat(questions.map { it.fullName }).doesNotContain("West Ham United", "Wolverhampton Wanderers", "Burnley")
        assertThat(questions).allSatisfy { question ->
            assertThat(question.league).isEqualTo(League.EPL)
            assertThat(question.hints.keys).containsExactlyInAnyOrder(*QuizDifficulty.entries.toTypedArray())
            assertThat(question.hints.values.flatten()).allSatisfy { hint ->
                assertThat(hint).isNotBlank().doesNotContainIgnoringCase(question.fullName)
                assertThat(hint).doesNotContain("This team plays as", "nickname starts with")
            }
        }
        assertThat(questions).allSatisfy { question ->
            assertThat(question.logoUrl).matches(
                "https://d2323rgqey2wlv\\.cloudfront\\.net/media/logos/epl/[a-f0-9]{64}\\.(png|jpeg)"
            )
        }
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
