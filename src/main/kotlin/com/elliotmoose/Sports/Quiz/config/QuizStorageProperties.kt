package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz")
data class QuizStorageProperties(
    val questions: StorageSettings = StorageSettings(),
    val results: StorageSettings = StorageSettings()
)

data class StorageSettings(
    val storage: String = "local"
)
