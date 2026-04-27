package com.elliotmoose.Sports.Quiz.ai

import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryRequest
import com.elliotmoose.Sports.Quiz.ai.model.QuizSummaryResponse
import com.elliotmoose.Sports.Quiz.quiz.model.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
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
class AiApiE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockBean
    private lateinit var aiSupplier: AiSupplier

    private val jsonHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    fun `quiz summary returns 200 with summary from ai supplier`() {
        whenever(aiSupplier.complete(any())).thenReturn("Not bad for a casual fan.")

        val quiz = requireNotNull(createAndCompleteQuiz())

        val response = restTemplate.exchange(
            "http://localhost:$port/v0/ai/quiz-summary",
            HttpMethod.POST,
            HttpEntity(QuizSummaryRequest(quizId = quiz.quizId), jsonHeaders),
            QuizSummaryResponse::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotNull
        assertThat(response.body!!.summary).isEqualTo("Not bad for a casual fan.")
    }

    @Test
    fun `quiz summary calls ai supplier with a non-blank prompt`() {
        whenever(aiSupplier.complete(any())).thenReturn("Some summary.")

        val quiz = requireNotNull(createAndCompleteQuiz())

        restTemplate.exchange(
            "http://localhost:$port/v0/ai/quiz-summary",
            HttpMethod.POST,
            HttpEntity(QuizSummaryRequest(quizId = quiz.quizId), jsonHeaders),
            QuizSummaryResponse::class.java
        )

        verify(aiSupplier).complete(
            org.mockito.kotlin.argThat { prompt -> prompt.isNotBlank() }
        )
    }

    @Test
    fun `quiz summary returns 404 for unknown quiz id`() {
        val response = restTemplate.exchange(
            "http://localhost:$port/v0/ai/quiz-summary",
            HttpMethod.POST,
            HttpEntity(QuizSummaryRequest(quizId = "does-not-exist"), jsonHeaders),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    // --- helpers ---

    private fun createAndCompleteQuiz(): QuizResponse? {
        val quiz = restTemplate.exchange(
            "http://localhost:$port/v0/quiz",
            HttpMethod.POST,
            HttpEntity(
                QuizRequest(
                    leagues = setOf(League.MLB),
                    numberOfQuestions = 1,
                    difficulty = QuizDifficulty.EASY,
                    type = QuizType.LOGO
                ),
                jsonHeaders
            ),
            QuizResponse::class.java
        ).body ?: return null

        val question = quiz.questions.single()
        restTemplate.exchange(
            "http://localhost:$port/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity(
                AnswerRequest(quizId = quiz.quizId, questionId = question.id, answer = question.fullName),
                jsonHeaders
            ),
            AnswerResponse::class.java
        )

        return quiz
    }
}
