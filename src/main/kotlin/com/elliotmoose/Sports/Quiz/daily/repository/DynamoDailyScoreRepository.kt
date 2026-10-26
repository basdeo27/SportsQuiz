package com.elliotmoose.Sports.Quiz.daily.repository

import com.elliotmoose.Sports.Quiz.daily.model.DailyScore
import com.elliotmoose.Sports.Quiz.daily.model.DailyScorePage
import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoDailyScoreRepository(
    private val client: DynamoDbClient,
    private val properties: DailyLeaderboardProperties
) : DailyScoreRepository {
    override fun saveFirst(score: DailyScore): DailyScore {
        try {
            client.putItem(PutItemRequest.builder().tableName(properties.tableName)
                .item(toItem(score)).conditionExpression("attribute_not_exists(participantId)").build())
            return score
        } catch (ex: ConditionalCheckFailedException) {
            return requireNotNull(findByParticipantAndDate(score.participantId, score.date)) {
                "Existing daily score disappeared after a conditional write."
            }
        }
    }

    override fun findByParticipantAndDate(participantId: String, date: String): DailyScore? {
        val item = client.getItem(GetItemRequest.builder().tableName(properties.tableName)
            .key(mapOf("participantId" to s(participantId), "challengeDate" to s(date)))
            .consistentRead(true).build()).item()
        return item.takeIf { it.isNotEmpty() }?.let(::fromItem)
    }

    override fun leaderboard(date: String, limit: Int, cursor: String?): DailyScorePage {
        val scope = "day:$date"
        val keys = DailyScoreCursor.decode(cursor, scope, 3)
        val request = QueryRequest.builder().tableName(properties.tableName)
            .indexName(INDEX_NAME).keyConditionExpression("challengeDate = :date")
            // Exclude anonymous records written before account-only entry was introduced.
            .filterExpression("attribute_exists(accountId)")
            .expressionAttributeValues(mapOf(":date" to s(date))).scanIndexForward(true).limit(limit + 1)
        if (keys != null) {
            requireValidDayKeys(keys, date)
            request.exclusiveStartKey(mapOf(
                "participantId" to s(keys[0]), "challengeDate" to s(keys[1]), "rankingKey" to s(keys[2])
            ))
        }
        val scores = mutableListOf<DailyScore>()
        var hasMore: Boolean
        do {
            request.limit(limit + 1 - scores.size)
            val response = client.query(request.build())
            scores.addAll(response.items().map(::fromItem).filter { it.accountId != null })
            hasMore = response.hasLastEvaluatedKey()
            if (hasMore) request.exclusiveStartKey(response.lastEvaluatedKey())
        } while (scores.size < limit + 1 && hasMore)
        val last = scores.take(limit).lastOrNull()
        return DailyScorePage(scores.take(limit), if ((scores.size > limit || hasMore) && last != null)
            DailyScoreCursor.encode(scope, last.participantId, last.date, last.rankingKey) else null)
    }

    override fun history(participantId: String, limit: Int, cursor: String?): DailyScorePage {
        val scope = "user:$participantId"
        val before = DailyScoreCursor.decode(cursor, scope, 1)?.single()
        val request = QueryRequest.builder().tableName(properties.tableName)
            .keyConditionExpression("participantId = :participant")
            .expressionAttributeValues(mapOf(":participant" to s(participantId)))
            .scanIndexForward(false).consistentRead(true).limit(limit + 1)
        if (before != null) request.exclusiveStartKey(mapOf("participantId" to s(participantId), "challengeDate" to s(before)))
        val response = client.query(request.build())
        val scores = response.items().map(::fromItem)
        val last = scores.take(limit).lastOrNull()
        return DailyScorePage(scores.take(limit), if ((scores.size > limit || response.hasLastEvaluatedKey()) && last != null)
            DailyScoreCursor.encode(scope, last.date) else null)
    }

    private fun requireValidDayKeys(keys: List<String>, date: String) {
        if (keys[1] != date) throw com.elliotmoose.Sports.Quiz.daily.InvalidDailyRequestException("Invalid pagination cursor date.")
    }

    private fun toItem(score: DailyScore): Map<String, AttributeValue> = buildMap {
        put("participantId", s(score.participantId))
        put("challengeDate", s(score.date))
        put("sessionId", s(score.sessionId))
        put("displayName", s(score.displayName))
        score.accountId?.let { put("accountId", s(it)) }
        put("score", n(score.score.toLong()))
        put("elapsedSeconds", n(score.elapsedSeconds))
        put("hintsRevealed", n(score.hintsRevealed.toLong()))
        put("completedAtMillis", n(score.completedAtMillis))
        put("gaveUp", AttributeValue.builder().bool(score.gaveUp).build())
        put("rankingKey", s(score.rankingKey))
    }

    private fun fromItem(item: Map<String, AttributeValue>) = DailyScore(
        participantId = item.getValue("participantId").s(), date = item.getValue("challengeDate").s(),
        sessionId = item.getValue("sessionId").s(), displayName = item.getValue("displayName").s(),
        accountId = item["accountId"]?.s(), score = item.getValue("score").n().toInt(),
        elapsedSeconds = item.getValue("elapsedSeconds").n().toLong(),
        hintsRevealed = item.getValue("hintsRevealed").n().toInt(),
        completedAtMillis = item.getValue("completedAtMillis").n().toLong(), gaveUp = item.getValue("gaveUp").bool()
    )

    private fun s(value: String) = AttributeValue.builder().s(value).build()
    private fun n(value: Long) = AttributeValue.builder().n(value.toString()).build()

    companion object { const val INDEX_NAME = "daily-ranking" }
}
