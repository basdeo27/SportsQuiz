package com.elliotmoose.Sports.Quiz.results.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.results")
data class ResultsStorageProperties(
    val storage: String = "local"
)
