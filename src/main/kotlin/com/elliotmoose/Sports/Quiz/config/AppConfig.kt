package com.elliotmoose.Sports.Quiz.config

import com.elliotmoose.Sports.Quiz.ai.properties.AiProperties
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizQuestionStorageProperties
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizSettingsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableConfigurationProperties(
    value = [
        QuizSettingsProperties::class,
        QuizQuestionStorageProperties::class,
        QuizDynamoProperties::class,
        QuizCorsProperties::class,
        AiProperties::class
    ]
)
class AppConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
