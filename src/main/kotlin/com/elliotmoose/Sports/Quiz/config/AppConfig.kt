package com.elliotmoose.Sports.Quiz.config

import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizQuestionStorageProperties
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizSettingsProperties
import com.elliotmoose.Sports.Quiz.results.properties.ResultsStorageProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    value = [
        QuizSettingsProperties::class,
        QuizQuestionStorageProperties::class,
        QuizDynamoProperties::class,
        ResultsStorageProperties::class,
        QuizCorsProperties::class
    ]
)
class AppConfig
