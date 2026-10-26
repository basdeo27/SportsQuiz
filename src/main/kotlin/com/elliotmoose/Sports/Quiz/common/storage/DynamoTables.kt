package com.elliotmoose.Sports.Quiz.common.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Component
class DynamoTables(private val client: DynamoDbClient) {
    fun ensureSingleKeyTable(table: String) {
        try {
            client.describeTable { it.tableName(table) }
        } catch (ex: ResourceNotFoundException) {
            try {
                client.createTable(CreateTableRequest.builder().tableName(table).billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()).build())
            } catch (ex: ResourceInUseException) {
                // Another instance is creating the same table; both wait for it below.
            }
        }
        client.waiter().use { waiter ->
            check(waiter.waitUntilTableExists { it.tableName(table) }.matched().response().isPresent) {
                "DynamoDB table $table did not become ready."
            }
        }
        val ttl = client.describeTimeToLive { it.tableName(table) }.timeToLiveDescription().timeToLiveStatus()
        if (ttl == TimeToLiveStatus.DISABLED) {
            client.updateTimeToLive { it.tableName(table).timeToLiveSpecification { spec ->
                spec.attributeName("expiresAtSeconds").enabled(true)
            } }
        }
    }
}
