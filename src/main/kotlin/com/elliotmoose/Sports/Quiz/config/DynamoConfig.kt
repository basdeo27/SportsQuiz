package com.elliotmoose.Sports.Quiz.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(
    value = [QuizDynamoProperties::class, QuizStorageProperties::class, QuizSettingsProperties::class]
)
class DynamoConfig(private val properties: QuizDynamoProperties) {

    @Bean
    fun dynamoDbClient(): DynamoDbClient {
        val credentialsProvider = resolveCredentials()
        val builder = DynamoDbClient.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(credentialsProvider)

        properties.endpoint?.let { endpoint ->
            builder.endpointOverride(URI.create(endpoint))
        }

        return builder.build()
    }

    private fun resolveCredentials(): AwsCredentialsProvider {
        val accessKey = properties.accessKey
        val secretKey = properties.secretKey
        if (!accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        }
        return DefaultCredentialsProvider.create()
    }
}
