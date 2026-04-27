package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoAccountRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : AccountRepository {

    private val logger = LoggerFactory.getLogger(DynamoAccountRepository::class.java)

    override fun save(account: Account): Account {
        logger.info("DynamoDB PUT: saving account id=${account.id} username=${account.username} table=${properties.accountsTableName}")
        dynamoDbClient.putItem(
            PutItemRequest.builder()
                .tableName(properties.accountsTableName)
                .item(toItem(account))
                .build()
        )
        return account
    }

    override fun findById(id: String): Account? {
        logger.info("DynamoDB GET: fetching account by id=$id table=${properties.accountsTableName}")
        val response = dynamoDbClient.getItem(
            GetItemRequest.builder()
                .tableName(properties.accountsTableName)
                .key(mapOf("id" to AttributeValue.builder().s(id).build()))
                .build()
        )
        return response.item().takeIf { it.isNotEmpty() }?.let { fromItem(it) }
    }

    override fun findByUsername(username: String): Account? {
        logger.info("DynamoDB SCAN: fetching account by username=$username table=${properties.accountsTableName}")
        val response = dynamoDbClient.scan(
            ScanRequest.builder()
                .tableName(properties.accountsTableName)
                .filterExpression("username = :username")
                .expressionAttributeValues(
                    mapOf(":username" to AttributeValue.builder().s(username.lowercase()).build())
                )
                .build()
        )
        return response.items().firstOrNull()?.let { fromItem(it) }
    }

    override fun delete(id: String) {
        logger.info("DynamoDB DELETE: deleting account id=$id table=${properties.accountsTableName}")
        dynamoDbClient.deleteItem(
            DeleteItemRequest.builder()
                .tableName(properties.accountsTableName)
                .key(mapOf("id" to AttributeValue.builder().s(id).build()))
                .build()
        )
    }

    private fun toItem(account: Account): Map<String, AttributeValue> = mapOf(
        "id" to AttributeValue.builder().s(account.id).build(),
        "username" to AttributeValue.builder().s(account.username.lowercase()).build(),
        "passwordHash" to AttributeValue.builder().s(account.passwordHash).build(),
        "nickname" to AttributeValue.builder().s(account.nickname).build(),
        "createdAtMillis" to AttributeValue.builder().n(account.createdAtMillis.toString()).build()
    )

    private fun fromItem(item: Map<String, AttributeValue>) = Account(
        id = item["id"]?.s().orEmpty(),
        username = item["username"]?.s().orEmpty(),
        passwordHash = item["passwordHash"]?.s().orEmpty(),
        nickname = item["nickname"]?.s().orEmpty(),
        createdAtMillis = item["createdAtMillis"]?.n()?.toLong() ?: 0L
    )
}
