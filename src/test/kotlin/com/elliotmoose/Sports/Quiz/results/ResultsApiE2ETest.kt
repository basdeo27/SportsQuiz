package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.quiz.model.*
import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import com.elliotmoose.Sports.Quiz.results.model.QuizResultsResponse
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
class ResultsApiE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val jsonHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    fun `results endpoint returns 200 with a valid response shape`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/v0/quiz/results?userId=single-user",
            QuizResultsResponse::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        assertThat(response.body!!.results).isNotNull
    }

    @Test
    fun `completing a quiz by answering all questions saves the result`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 1).body)
        val question = quiz.questions.single()

        answerQuestion(quiz.quizId, question.id, question.fullName)

        val result = findResult(quiz.quizId)

        assertThat(result).isNotNull
        requireNotNull(result).apply {
            assertThat(quizId).isEqualTo(quiz.quizId)
            assertThat(difficulty).isEqualTo(QuizDifficulty.EASY)
            assertThat(leagues).contains(League.MLB)
            assertThat(totalQuestions).isEqualTo(1)
            assertThat(correctCount).isEqualTo(1)
            assertThat(incorrectCount).isEqualTo(0)
            assertThat(skippedCount).isEqualTo(0)
            assertThat(score).isGreaterThan(0)
            assertThat(completedAtMillis).isGreaterThan(0)
            assertThat(elapsedSeconds).isGreaterThanOrEqualTo(0)
        }
    }

    @Test
    fun `completing a quiz by skipping all questions saves the result`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 2).body)

        skipQuestion(quiz.quizId, quiz.questions[0].id)
        skipQuestion(quiz.quizId, quiz.questions[1].id)

        val result = findResult(quiz.quizId)

        assertThat(result).isNotNull
        requireNotNull(result).apply {
            assertThat(totalQuestions).isEqualTo(2)
            assertThat(skippedCount).isEqualTo(2)
            assertThat(correctCount).isEqualTo(0)
            assertThat(incorrectCount).isEqualTo(0)
            assertThat(score).isEqualTo(0)
        }
    }

    @Test
    fun `incomplete quiz does not appear in results`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 2).body)

        // Answer only the first of two questions — quiz is not yet complete
        answerQuestion(quiz.quizId, quiz.questions[0].id, quiz.questions[0].fullName)

        val result = findResult(quiz.quizId)

        assertThat(result).isNull()
    }

    @Test
    fun `result reflects correct mix of answered and skipped questions`() {
        val quiz = requireNotNull(createLogoQuiz(numberOfQuestions = 3).body)

        answerQuestion(quiz.quizId, quiz.questions[0].id, quiz.questions[0].fullName)
        skipQuestion(quiz.quizId, quiz.questions[1].id)
        skipQuestion(quiz.quizId, quiz.questions[2].id)

        val result = findResult(quiz.quizId)

        assertThat(result).isNotNull
        requireNotNull(result).apply {
            assertThat(totalQuestions).isEqualTo(3)
            assertThat(correctCount).isEqualTo(1)
            assertThat(skippedCount).isEqualTo(2)
            assertThat(incorrectCount).isEqualTo(0)
        }
    }

    @Test
    fun `multiple completed quizzes all appear in results`() {
        val quizOne = requireNotNull(createLogoQuiz(numberOfQuestions = 1).body)
        val quizTwo = requireNotNull(createLogoQuiz(numberOfQuestions = 1).body)

        answerQuestion(quizOne.quizId, quizOne.questions[0].id, quizOne.questions[0].fullName)
        skipQuestion(quizTwo.quizId, quizTwo.questions[0].id)

        val results = getResults()

        assertThat(results.map { it.quizId }).contains(quizOne.quizId, quizTwo.quizId)
    }

    // --- helpers ---

    private fun createLogoQuiz(numberOfQuestions: Int): ResponseEntity<QuizResponse> {
        return restTemplate.exchange(
            "http://localhost:$port/v0/quiz",
            HttpMethod.POST,
            HttpEntity(
                QuizRequest(
                    leagues = setOf(League.MLB),
                    numberOfQuestions = numberOfQuestions,
                    difficulty = QuizDifficulty.EASY,
                    type = QuizType.LOGO
                ),
                jsonHeaders
            ),
            QuizResponse::class.java
        )
    }

    private fun answerQuestion(quizId: String, questionId: String, answer: String) {
        restTemplate.exchange(
            "http://localhost:$port/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity(AnswerRequest(quizId = quizId, questionId = questionId, answer = answer), jsonHeaders),
            AnswerResponse::class.java
        )
    }

    private fun skipQuestion(quizId: String, questionId: String) {
        restTemplate.exchange(
            "http://localhost:$port/v0/quiz/skip",
            HttpMethod.POST,
            HttpEntity(SkipRequest(quizId = quizId, questionId = questionId), jsonHeaders),
            SkipResponse::class.java
        )
    }

    private fun getResults(): List<QuizResult> {
        return restTemplate
            .getForEntity("http://localhost:$port/v0/quiz/results?userId=single-user", QuizResultsResponse::class.java)
            .body
            ?.results
            ?: emptyList()
    }

    private fun findResult(quizId: String): QuizResult? =
        getResults().find { it.quizId == quizId }
}
