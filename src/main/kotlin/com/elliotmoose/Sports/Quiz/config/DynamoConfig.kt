package com.elliotmoose.Sports.Quiz.config

import java.net.URI
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

@Configuration
@EnableConfigurationProperties(QuizDynamoProperties::class)
@ConditionalOnProperty(prefix = "quiz.dynamo", name = ["enabled"], havingValue = "true")
class DynamoConfig(private val properties: QuizDynamoProperties) {

    @Bean
    fun dynamoDbClient(): DynamoDbClient {
        val builder = DynamoDbClient.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("dummy", "dummy")
                )
            )

        properties.endpoint?.let { endpoint ->
            builder.endpointOverride(URI.create(endpoint))
        }

        return builder.build()
    }
}
