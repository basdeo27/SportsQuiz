package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Component
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DailyScoreTableInitializer(
    private val client: DynamoDbClient,
    private val properties: DailyLeaderboardProperties
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        try {
            val table = client.describeTable { it.tableName(properties.tableName) }.table()
            check(table.globalSecondaryIndexes().any { it.indexName() == DynamoDailyScoreRepository.INDEX_NAME }) {
                "Daily scores table is missing the daily-ranking index. See .agent/daily-leaderboard.md."
            }
            return
        } catch (ex: ResourceNotFoundException) {
            client.createTable(CreateTableRequest.builder().tableName(properties.tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(listOf("participantId", "challengeDate", "rankingKey").map { name ->
                    AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build()
                })
                .keySchema(key("participantId", KeyType.HASH), key("challengeDate", KeyType.RANGE))
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder().indexName(DynamoDailyScoreRepository.INDEX_NAME)
                    .keySchema(key("challengeDate", KeyType.HASH), key("rankingKey", KeyType.RANGE))
                    .projection { it.projectionType(ProjectionType.ALL) }.build())
                .build())
            client.waiter().use { waiter ->
                val result = waiter.waitUntilTableExists { it.tableName(properties.tableName) }
                check(result.matched().response().isPresent) { "Daily scores table did not become ready." }
            }
        }
    }

    private fun key(name: String, type: KeyType) = KeySchemaElement.builder().attributeName(name).keyType(type).build()
}
