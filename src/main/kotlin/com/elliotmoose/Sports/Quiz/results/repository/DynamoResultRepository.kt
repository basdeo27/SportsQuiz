package com.elliotmoose.Sports.Quiz.results.repository

import com.elliotmoose.Sports.Quiz.quiz.model.League
import com.elliotmoose.Sports.Quiz.quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.quiz.model.QuizType
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

@Service
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "dynamo")
class DynamoResultRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : ResultRepository {

    private val logger = LoggerFactory.getLogger(DynamoResultRepository::class.java)

    override fun saveResult(result: QuizResult) {
        logger.info("DynamoDB PUT: saving result for userId=${result.userId} quizId=${result.quizId} table=${properties.resultsTableName}")
        val item = mapOf(
            "userId" to AttributeValue.builder().s(result.userId).build(),
            "completedAtMillis" to AttributeValue.builder().n(result.completedAtMillis.toString()).build(),
            "quizId" to AttributeValue.builder().s(result.quizId).build(),
            "quizType" to AttributeValue.builder().s(result.quizType.name).build(),
            "difficulty" to AttributeValue.builder().s(result.difficulty.name).build(),
            "leagues" to AttributeValue.builder().ss(result.leagues.map { it.name }).build(),
            "totalQuestions" to AttributeValue.builder().n(result.totalQuestions.toString()).build(),
            "correctCount" to AttributeValue.builder().n(result.correctCount.toString()).build(),
            "incorrectCount" to AttributeValue.builder().n(result.incorrectCount.toString()).build(),
            "skippedCount" to AttributeValue.builder().n(result.skippedCount.toString()).build(),
            "elapsedSeconds" to AttributeValue.builder().n(result.elapsedSeconds.toString()).build(),
            "score" to AttributeValue.builder().n(result.score.toString()).build()
        )

        dynamoDbClient.putItem(
            PutItemRequest.builder()
                .tableName(properties.resultsTableName)
                .item(item)
                .build()
        )
    }

    override fun getResults(userId: String, limit: Int, before: Long?): List<QuizResult> {
        val expressionNames = mutableMapOf("#userId" to "userId")
        val expressionValues = mutableMapOf(
            ":userId" to AttributeValue.builder().s(userId).build()
        )
        val keyCondition = if (before != null) {
            expressionNames["#completedAt"] = "completedAtMillis"
            expressionValues[":before"] = AttributeValue.builder().n(before.toString()).build()
            "#userId = :userId AND #completedAt < :before"
        } else {
            "#userId = :userId"
        }

        val request = QueryRequest.builder()
            .tableName(properties.resultsTableName)
            .keyConditionExpression(keyCondition)
            .expressionAttributeNames(expressionNames)
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)
            .limit(limit)
            .build()

        logger.info("DynamoDB QUERY: fetching up to $limit results for userId=$userId before=$before table=${properties.resultsTableName}")
        val response = dynamoDbClient.query(request)
        logger.info("DynamoDB QUERY: retrieved ${response.count()} results")
        return response.items().map { item ->
            val leagues = item["leagues"]?.ss()?.mapNotNull { name ->
                runCatching { League.valueOf(name) }.getOrNull()
            }?.toSet() ?: emptySet()
            QuizResult(
                quizId = item["quizId"]?.s().orEmpty(),
                userId = item["userId"]?.s().orEmpty(),
                quizType = runCatching { QuizType.valueOf(item["quizType"]?.s().orEmpty()) }.getOrElse { QuizType.LOGO },
                difficulty = runCatching { QuizDifficulty.valueOf(item["difficulty"]?.s().orEmpty()) }
                    .getOrElse { QuizDifficulty.EASY },
                leagues = leagues,
                totalQuestions = item["totalQuestions"]?.n()?.toInt() ?: 0,
                correctCount = item["correctCount"]?.n()?.toInt() ?: 0,
                incorrectCount = item["incorrectCount"]?.n()?.toInt() ?: 0,
                skippedCount = item["skippedCount"]?.n()?.toInt() ?: 0,
                elapsedSeconds = item["elapsedSeconds"]?.n()?.toLong() ?: 0L,
                score = item["score"]?.n()?.toInt() ?: 0,
                completedAtMillis = item["completedAtMillis"]?.n()?.toLong() ?: 0L
            )
        }
    }
}
