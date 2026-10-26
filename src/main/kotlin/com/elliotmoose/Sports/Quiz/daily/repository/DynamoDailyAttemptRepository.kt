package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.common.storage.DynamoTables
import com.elliotmoose.Sports.Quiz.daily.model.DailySession
import com.elliotmoose.Sports.Quiz.daily.properties.DailyAttemptProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoDailyAttemptRepository(
    private val client: DynamoDbClient,
    private val properties: DailyAttemptProperties,
    private val mapper: ObjectMapper,
    private val tables: DynamoTables
) : DailyAttemptRepository, ApplicationRunner {
    override fun run(args: ApplicationArguments) { tables.ensureSingleKeyTable(properties.tableName) }
    override fun createIfAbsent(session: DailySession): DailySession {
        try {
            client.putItem(PutItemRequest.builder().tableName(properties.tableName).item(item(session))
                .conditionExpression("attribute_not_exists(id)").build())
            return session
        } catch (ex: ConditionalCheckFailedException) {
            return requireNotNull(find(session.id))
        }
    }
    override fun find(id: String): DailySession? {
        val item = client.getItem(GetItemRequest.builder().tableName(properties.tableName)
            .key(mapOf("id" to s(id))).consistentRead(true).build()).item()
        return item["payload"]?.s()?.let { mapper.readValue(it, DailySession::class.java) }
    }
    override fun replace(expectedVersion: Long, updated: DailySession): Boolean {
        return try {
            client.putItem(PutItemRequest.builder().tableName(properties.tableName).item(item(updated))
                .conditionExpression("#version = :expected")
                .expressionAttributeNames(mapOf("#version" to "version"))
                .expressionAttributeValues(mapOf(":expected" to n(expectedVersion))).build())
            true
        } catch (ex: ConditionalCheckFailedException) { false }
    }
    private fun item(session: DailySession): Map<String, AttributeValue> = buildMap {
        put("id", s(session.id)); put("version", n(session.version)); put("payload", s(mapper.writeValueAsString(session)))
        if (!session.ranked) put("expiresAtSeconds", n(session.startTimeMillis / 1000 + 86400))
    }
    private fun s(value: String) = AttributeValue.builder().s(value).build()
    private fun n(value: Long) = AttributeValue.builder().n(value.toString()).build()
}
