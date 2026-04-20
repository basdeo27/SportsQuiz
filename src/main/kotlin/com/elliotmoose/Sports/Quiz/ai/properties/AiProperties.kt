package com.elliotmoose.Sports.Quiz.ai.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
data class AiProperties(
    val mistral: MistralProperties = MistralProperties()
)

data class MistralProperties(
    val apiKey: String = "",
    val model: String = "mistral-small-latest"
)
