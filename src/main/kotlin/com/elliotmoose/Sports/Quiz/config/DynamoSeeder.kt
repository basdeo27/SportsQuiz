package com.elliotmoose.Sports.Quiz.config

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.TeamEntry
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Configuration
@ConditionalOnProperty(prefix = "quiz.dynamo", name = ["enabled"], havingValue = "true")
class DynamoSeeder(
    private val dynamoDbClient: DynamoDbClient,
    private val objectMapper: ObjectMapper,
    private val properties: QuizDynamoProperties
) {
    private val logger = LoggerFactory.getLogger(DynamoSeeder::class.java)

    @Bean
    fun dynamoSeederRunner(): ApplicationRunner {
        return ApplicationRunner {
            if (!properties.seed) {
                return@ApplicationRunner
            }
            try {
                ensureTable()
                seedData()
            } catch (ex: Exception) {
                logger.warn("Skipping DynamoDB seed due to error: ${ex.message}")
            }
        }
    }

    private fun ensureTable() {
        try {
            dynamoDbClient.describeTable(
                DescribeTableRequest.builder().tableName(properties.tableName).build()
            )
        } catch (ex: ResourceNotFoundException) {
            val request = CreateTableRequest.builder()
                .tableName(properties.tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("league")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("id")
                        .attributeType(ScalarAttributeType.S)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("league")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("id")
                        .keyType(KeyType.RANGE)
                        .build()
                )
                .build()

            dynamoDbClient.createTable(request)
            waitForTable()
        }
    }

    private fun seedData() {
        League.entries.forEach { league ->
            val teams = loadTeams(league)
            teams.forEach { team ->
                val item = mapOf(
                    "league" to AttributeValue.builder().s(league.name).build(),
                    "id" to AttributeValue.builder().s(team.id).build(),
                    "name" to AttributeValue.builder().s(team.name).build(),
                    "logoUrl" to AttributeValue.builder().s(team.logoUrl).build(),
                    "answers" to AttributeValue.builder().ss(team.answers).build()
                )
                dynamoDbClient.putItem { builder ->
                    builder.tableName(properties.tableName)
                    builder.item(item)
                }
            }
        }
    }

    private fun waitForTable() {
        val maxAttempts = 10
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName(properties.tableName).build()
                )
                return
            } catch (ex: ResourceNotFoundException) {
                Thread.sleep(300L)
            }
        }
    }

    private fun loadTeams(league: League): List<TeamEntry> {
        val resource = ClassPathResource("data/${league.name.lowercase()}.json")
        return objectMapper.readValue(resource.inputStream, object : TypeReference<List<TeamEntry>>() {})
    }
}
