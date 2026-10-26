package com.elliotmoose.Sports.Quiz.pact

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.LambdaDsl
import au.com.dius.pact.consumer.dsl.PactBuilder
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.*
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate

/**
 * Consumer-side Pact tests. Each @Pact method defines one interaction (request + expected response)
 * and writes it to build/pacts/sports-quiz-frontend-sports-quiz-api.json.
 *
 * The @Test method verifies that our client code handles the mocked response correctly.
 * The mock server is provided by Pact — no real Spring Boot app is started here.
 */
@ExtendWith(PactConsumerTestExt::class)
@PactTestFor(providerName = "sports-quiz-api", port = "0")
class QuizApiPactConsumerTest {

    // A RestTemplate that never throws on 4xx/5xx so we can assert the response ourselves.
    private val restTemplate = RestTemplate().apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse) = false
            override fun handleError(response: ClientHttpResponse) {}
        }
    }

    private val jsonHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    // --- Pact definitions ---

    @Pact(consumer = "sports-quiz-frontend", provider = "sports-quiz-api")
    fun quizNotFoundPact(builder: PactBuilder): V4Pact =
        builder
            .usingLegacyDsl()
            .given("quiz does not exist")
            .uponReceiving("an answer request for a non-existent quiz")
            .method("POST")
            .path("/v0/quiz/answer")
            .headers(mapOf("Content-Type" to "application/json"))
            .body("""{"quizId":"unknown-quiz-id","questionId":"q1","answer":"test"}""")
            .willRespondWith()
            .status(404)
            .body(LambdaDsl.newJsonBody { body ->
                body.stringType("message")
            }.build())
            .toPact(V4Pact::class.java)

    // --- Tests against the Pact mock server ---

    @Test
    @PactTestFor(pactMethod = "quizNotFoundPact")
    fun `answering a question on an unknown quiz returns 404 with a message`(mockServer: MockServer) {
        val response = restTemplate.exchange(
            "${mockServer.getUrl()}/v0/quiz/answer",
            HttpMethod.POST,
            HttpEntity("""{"quizId":"unknown-quiz-id","questionId":"q1","answer":"test"}""", jsonHeaders),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).contains("message")
    }
}
