package com.elliotmoose.Sports.Quiz.account.repository

import com.elliotmoose.Sports.Quiz.account.AccountAlreadyExistsException
import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoAccountRepository(
    private val client: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : AccountRepository {
    override fun save(account: Account): Account {
        client.putItem { it.tableName(properties.accountsTableName).item(toItem(account)) }
        return account
    }

    override fun create(account: Account): Account = writeEmailClaim(account, "attribute_not_exists(id)")
    override fun claimEmail(account: Account): Account = writeEmailClaim(account, "attribute_exists(id) AND attribute_not_exists(email)")

    private fun writeEmailClaim(account: Account, accountCondition: String): Account {
        val email = requireNotNull(account.email).lowercase()
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                TransactWriteItem.builder().put(Put.builder().tableName(properties.accountsTableName)
                    .item(mapOf("id" to s("EMAIL#$email"), "accountId" to s(account.id)))
                    .conditionExpression("attribute_not_exists(id)").build()).build(),
                TransactWriteItem.builder().put(Put.builder().tableName(properties.accountsTableName)
                    .item(toItem(account)).conditionExpression(accountCondition).build()).build()
            ).build())
        } catch (ex: TransactionCanceledException) {
            if (ex.cancellationReasons().any { it.code() == "ConditionalCheckFailed" }) throw AccountAlreadyExistsException(email)
            throw ex
        }
        return account
    }

    override fun findByEmail(email: String): Account? {
        val lookup = read("EMAIL#${email.lowercase()}")
        return lookup["accountId"]?.s()?.let(::findById)
    }
    override fun findById(id: String): Account? = read(id).takeIf { it.containsKey("passwordHash") }?.let(::fromItem)
    private fun read(id: String): Map<String, AttributeValue> = client.getItem(GetItemRequest.builder()
        .tableName(properties.accountsTableName).key(mapOf("id" to s(id))).consistentRead(true).build()).item()

    // Legacy migration and the development seeder only. Normal login uses direct email keys.
    override fun findByUsername(username: String): Account? {
        val request = ScanRequest.builder().tableName(properties.accountsTableName)
            .filterExpression("username = :username AND attribute_exists(passwordHash)")
            .expressionAttributeValues(mapOf(":username" to s(username.lowercase())))
        do {
            val response = client.scan(request.build())
            response.items().firstOrNull()?.let { return fromItem(it) }
            if (!response.hasLastEvaluatedKey()) return null
            request.exclusiveStartKey(response.lastEvaluatedKey())
        } while (true)
    }

    override fun delete(id: String) {
        val account = findById(id) ?: return
        val deletes = mutableListOf(TransactWriteItem.builder().delete(Delete.builder()
            .tableName(properties.accountsTableName).key(mapOf("id" to s(id))).build()).build())
        account.email?.let { email -> deletes.add(TransactWriteItem.builder().delete(Delete.builder()
            .tableName(properties.accountsTableName).key(mapOf("id" to s("EMAIL#${email.lowercase()}")))
            .conditionExpression("accountId = :id").expressionAttributeValues(mapOf(":id" to s(id))).build()).build()) }
        client.transactWriteItems { it.transactItems(deletes) }
    }

    private fun toItem(account: Account): Map<String, AttributeValue> = buildMap {
        put("id", s(account.id)); put("username", s(account.username.lowercase()))
        put("passwordHash", s(account.passwordHash)); put("nickname", s(account.nickname))
        put("createdAtMillis", AttributeValue.builder().n(account.createdAtMillis.toString()).build())
        account.email?.let { put("email", s(it.lowercase())) }
        put("emailVerified", AttributeValue.builder().bool(account.emailVerified).build())
    }
    private fun fromItem(item: Map<String, AttributeValue>) = Account(
        id = item.getValue("id").s(), username = item.getValue("username").s(), passwordHash = item.getValue("passwordHash").s(),
        nickname = item.getValue("nickname").s(), createdAtMillis = item.getValue("createdAtMillis").n().toLong(),
        email = item["email"]?.s(), emailVerified = item["emailVerified"]?.bool() ?: false
    )
    private fun s(value: String) = AttributeValue.builder().s(value).build()
}
