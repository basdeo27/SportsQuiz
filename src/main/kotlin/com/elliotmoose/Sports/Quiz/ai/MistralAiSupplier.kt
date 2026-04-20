package com.elliotmoose.Sports.Quiz.ai

import com.elliotmoose.Sports.Quiz.ai.properties.AiProperties
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class MistralAiSupplier(
    private val properties: AiProperties,
    private val objectMapper: ObjectMapper
) : AiSupplier {

    private val log = LoggerFactory.getLogger(MistralAiSupplier::class.java)
    private val httpClient = HttpClient.newHttpClient()

    override fun complete(prompt: String): String {
        val apiKey = properties.mistral.apiKey
        if (apiKey.isBlank()) {
            log.warn("Mistral API key not configured — returning fallback summary")
            return FALLBACK_SUMMARY
        }

        val requestBody = objectMapper.writeValueAsString(
            MistralRequest(
                model = properties.mistral.model,
                messages = listOf(MistralMessage(role = "user", content = prompt)),
                maxTokens = 300
            )
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mistral.ai/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            log.error("Mistral API returned status ${response.statusCode()}: ${response.body()}")
            return FALLBACK_SUMMARY
        }

        val parsed = objectMapper.readValue(response.body(), MistralResponse::class.java)
        return parsed.choices.firstOrNull()?.message?.content?.trim() ?: FALLBACK_SUMMARY
    }

    companion object {
        private const val FALLBACK_SUMMARY =
            "Great effort on the quiz! The AI scorecard is temporarily unavailable, but your sports knowledge speaks for itself."
    }
}

private data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val maxTokens: Int
)

private data class MistralMessage(
    val role: String,
    val content: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MistralResponse(
    val choices: List<MistralChoice> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MistralChoice(
    val message: MistralMessage = MistralMessage("assistant", "")
)
