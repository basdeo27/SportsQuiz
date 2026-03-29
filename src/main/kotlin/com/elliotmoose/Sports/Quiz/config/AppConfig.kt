package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    value = [
        QuizSettingsProperties::class,
        QuizStorageProperties::class,
        QuizDynamoProperties::class,
        QuizCorsProperties::class
    ]
)
class AppConfig
