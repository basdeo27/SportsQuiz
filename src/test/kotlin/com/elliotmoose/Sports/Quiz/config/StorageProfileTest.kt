package com.elliotmoose.Sports.Quiz.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.env.Profiles

class StorageProfileTest {
    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())

    @Test
    fun `plain startup defaults to local DynamoDB with placeholder credentials`() {
        runner.run { context ->
            val environment = context.environment

            assertThat(environment.acceptsProfiles(Profiles.of("local"))).isTrue()
            assertThat(environment.getProperty("quiz.auth.require-email-verification")).isEqualTo("false")
            assertThat(environment.getProperty("quiz.auth.allow-development-login")).isEqualTo("true")
            assertThat(environment.getProperty("quiz.storage")).isEqualTo(System.getenv("QUIZ_STORAGE") ?: "dynamo")
            assertThat(environment.getProperty("quiz.dynamo.endpoint")).isEqualTo("http://localhost:8000")
            assertThat(environment.getProperty("quiz.dynamo.access-key")).isEqualTo("local")
            assertThat(environment.getProperty("quiz.dynamo.secret-key")).isEqualTo("local")
        }
    }

    @Test
    fun `explicit production profile uses AWS without activating local profile`() {
        runner.withPropertyValues("spring.profiles.active=prod").run { context ->
            val environment = context.environment

            assertThat(environment.acceptsProfiles(Profiles.of("prod"))).isTrue()
            assertThat(environment.getProperty("quiz.auth.require-email-verification")).isEqualTo("true")
            assertThat(environment.getProperty("quiz.auth.allow-development-login")).isEqualTo("false")
            assertThat(environment.getProperty("quiz.auth.secure-cookie")).isEqualTo("true")
            assertThat(environment.acceptsProfiles(Profiles.of("local"))).isFalse()
            assertThat(environment.getProperty("quiz.dynamo.endpoint"))
                .isEqualTo("https://dynamodb.eu-west-2.amazonaws.com")
        }
    }

    @Test
    fun `dev profile also uses local DynamoDB`() {
        runner.withPropertyValues("spring.profiles.active=dev").run { context ->
            assertThat(context.environment.getProperty("quiz.dynamo.endpoint")).isEqualTo("http://localhost:8000")
            assertThat(context.environment.getProperty("quiz.dynamo.access-key")).isEqualTo("local")
        }
    }
}
