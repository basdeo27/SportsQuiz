package com.elliotmoose.Sports.Quiz.config

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.TeamEntry
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Configuration
class DynamoSeeder(
    private val dynamoDbClient: DynamoDbClient,
    private val objectMapper: ObjectMapper,
    private val properties: QuizDynamoProperties,
    private val storageProperties: QuizStorageProperties
) {
    private val logger = LoggerFactory.getLogger(DynamoSeeder::class.java)

    @Bean
    fun dynamoSeederRunner(): ApplicationRunner {
        return ApplicationRunner {
            if (storageProperties.results.storage == "dynamo") {
                try {
                    ensureResultsTable()
                } catch (ex: Exception) {
                    logger.warn("Skipping DynamoDB results table setup due to error: ${ex.message}")
                }
            }

            if (storageProperties.questions.storage != "dynamo" || !properties.seed) {
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
                DescribeTableRequest.builder().tableName(properties.teamsTableName).build()
            )
        } catch (ex: ResourceNotFoundException) {
            val request = CreateTableRequest.builder()
                .tableName(properties.teamsTableName)
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

    private fun ensureResultsTable() {
        try {
            dynamoDbClient.describeTable(
                DescribeTableRequest.builder().tableName(properties.resultsTableName).build()
            )
        } catch (ex: ResourceNotFoundException) {
            val request = CreateTableRequest.builder()
                .tableName(properties.resultsTableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                    AttributeDefinition.builder()
                        .attributeName("userId")
                        .attributeType(ScalarAttributeType.S)
                        .build(),
                    AttributeDefinition.builder()
                        .attributeName("completedAtMillis")
                        .attributeType(ScalarAttributeType.N)
                        .build()
                )
                .keySchema(
                    KeySchemaElement.builder()
                        .attributeName("userId")
                        .keyType(KeyType.HASH)
                        .build(),
                    KeySchemaElement.builder()
                        .attributeName("completedAtMillis")
                        .keyType(KeyType.RANGE)
                        .build()
                )
                .build()

            dynamoDbClient.createTable(request)
            waitForTable(properties.resultsTableName)
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
                val finalItem = if (!team.hint.isNullOrBlank()) {
                    item + ("hint" to AttributeValue.builder().s(team.hint).build())
                } else {
                    item
                }
                dynamoDbClient.putItem { builder ->
                    builder.tableName(properties.teamsTableName)
                    builder.item(finalItem)
                }
            }
        }
    }

    private fun waitForTable(tableName: String = properties.teamsTableName) {
        val maxAttempts = 10
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build()
                )
                return
            } catch (ex: ResourceNotFoundException) {
                Thread.sleep(300L)
            }
        }
    }

    private fun loadTeams(league: League): List<TeamEntry> {
        val resource = ClassPathResource("data/logos/${league.name.lowercase()}.json")
        return objectMapper.readValue(resource.inputStream, object : TypeReference<List<TeamEntry>>() {})
    }
}
