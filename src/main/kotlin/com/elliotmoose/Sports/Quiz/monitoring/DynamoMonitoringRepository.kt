package com.elliotmoose.Sports.Quiz.monitoring

import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import com.elliotmoose.Sports.Quiz.common.storage.DynamoTables
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoMonitoringRepository(private val client: DynamoDbClient, private val properties: MonitoringProperties,
    private val tables: DynamoTables) : MonitoringRepository, ApplicationRunner {
    override fun run(args: ApplicationArguments) { tables.ensureSingleKeyTable(properties.tableName) }
    override fun completed(id: String, type: CompletionType, date: String, at: Long) {
        fun update(key: String) = TransactWriteItem.builder().update(Update.builder().tableName(properties.tableName)
            .key(mapOf("id" to s(key)))
            .updateExpression("SET trackingSinceMillis = if_not_exists(trackingSinceMillis, :at) ADD #kind :one")
            .expressionAttributeNames(mapOf("#kind" to type.name))
            .expressionAttributeValues(mapOf(":at" to n(at), ":one" to n(1))).build()).build()
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                TransactWriteItem.builder().put(Put.builder().tableName(properties.tableName)
                    .item(mapOf("id" to s("EVENT#$id"))).conditionExpression("attribute_not_exists(id)").build()).build(),
                update("TOTAL"), update("DAY#$date")
            ).build())
        } catch (ex: TransactionCanceledException) {
            if (ex.cancellationReasons().firstOrNull()?.code() != "ConditionalCheckFailed") throw ex
        }
    }
    override fun error(date: String, method: String, route: String, status: Int) {
        client.updateItem { it.tableName(properties.tableName).key(mapOf("id" to s("ERRORS#$date")))
            .updateExpression("ADD #endpoint :one")
            .expressionAttributeNames(mapOf("#endpoint" to "$method|$route|$status"))
            .expressionAttributeValues(mapOf(":one" to n(1))) }
    }
    override fun summary(date: String): MonitoringSummary {
        fun read(key: String) = client.getItem(GetItemRequest.builder().tableName(properties.tableName)
            .key(mapOf("id" to s(key))).consistentRead(true).build()).item()
        fun counts(item: Map<String, AttributeValue>) = CompletionType.entries.associate { it.name to (item[it.name]?.n()?.toLong() ?: 0) }
        val total = read("TOTAL"); val daily = read("DAY#$date"); val errors = read("ERRORS#$date")
        val all = counts(total)
        return MonitoringSummary(date, total["trackingSinceMillis"]?.n()?.toLong(), all.values.sum(), all, counts(daily),
            errors.filterKeys { it.contains('|') }.map { (key, value) -> val parts = key.split('|'); EndpointErrorCount(parts[0], parts[1], parts[2].toInt(), value.n().toLong()) }.sortedByDescending { it.count })
    }
    private fun s(value: String) = AttributeValue.builder().s(value).build()
    private fun n(value: Long) = AttributeValue.builder().n(value.toString()).build()
}
