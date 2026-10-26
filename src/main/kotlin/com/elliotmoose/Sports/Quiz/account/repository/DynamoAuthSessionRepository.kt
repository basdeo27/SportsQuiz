package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.common.storage.DynamoTables
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoAuthSessionRepository(
    private val client: DynamoDbClient,
    private val properties: AuthenticationProperties,
    private val tables: DynamoTables
) : AuthSessionRepository, ApplicationRunner {
    override fun run(args: ApplicationArguments) { tables.ensureSingleKeyTable(properties.sessionsTableName) }
    override fun saveIfExpired(session: AuthSession, now: Long): Boolean = try {
        client.putItem(PutItemRequest.builder().tableName(properties.sessionsTableName).item(item(session))
            .conditionExpression("attribute_not_exists(id) OR expiresAt <= :now")
            .expressionAttributeValues(mapOf(":now" to AttributeValue.builder().n(now.toString()).build())).build())
        true
    } catch (ex: ConditionalCheckFailedException) { false }

    override fun save(session: AuthSession) {
        client.putItem { it.tableName(properties.sessionsTableName).item(item(session)) }
    }
    private fun item(session: AuthSession) = mapOf(
        "purpose" to s(session.purpose), "id" to s(session.id), "accountId" to s(session.accountId),
        "credentialFingerprint" to s(session.credentialFingerprint),
        "expiresAt" to AttributeValue.builder().n(session.expiresAt.toString()).build(),
        "expiresAtSeconds" to AttributeValue.builder().n((session.expiresAt / 1000).toString()).build()
    )
    override fun find(id: String): AuthSession? {
        val item = client.getItem(GetItemRequest.builder().tableName(properties.sessionsTableName)
            .key(mapOf("id" to s(id))).consistentRead(true).build()).item()
        if (item.isEmpty()) return null
        return AuthSession(item.getValue("id").s(), item.getValue("accountId").s(),
            item.getValue("credentialFingerprint").s(), item.getValue("expiresAt").n().toLong(), item["purpose"]?.s() ?: "SESSION")
    }
    override fun consume(id: String): AuthSession? {
        val item = client.deleteItem(DeleteItemRequest.builder().tableName(properties.sessionsTableName)
            .key(mapOf("id" to s(id))).returnValues(ReturnValue.ALL_OLD).build()).attributes()
        if (item.isEmpty()) return null
        return AuthSession(item.getValue("id").s(), item.getValue("accountId").s(),
            item.getValue("credentialFingerprint").s(), item.getValue("expiresAt").n().toLong(), item["purpose"]?.s() ?: "SESSION")
    }
    override fun delete(id: String) {
        client.deleteItem { it.tableName(properties.sessionsTableName).key(mapOf("id" to s(id))) }
    }
    private fun s(value: String) = AttributeValue.builder().s(value).build()
}
