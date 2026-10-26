package com.elliotmoose.Sports.Quiz.error

import com.elliotmoose.Sports.Quiz.quiz.model.AnswerRequest
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
class ApiErrorContractTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private val jsonHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @Test
    fun `answering a question on an unknown quiz returns 404 with a message`() {
        val response = restTemplate.exchange(
            "http://localhost:$port/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity(
                AnswerRequest(quizId = "does-not-exist", questionId = "q1", answer = "anything"),
                jsonHeaders
            ),
            ApiError::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).isNotNull
        assertThat(response.body!!.message).isNotBlank()
    }
}
