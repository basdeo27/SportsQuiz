package com.elliotmoose.Sports.Quiz.repository

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import com.elliotmoose.Sports.Quiz.config.QuizDynamoProperties
import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.model.QuizResult
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

@Service
@ConditionalOnProperty(prefix = "quiz.dynamo", name = ["enabled"], havingValue = "true")
class DynamoQuizRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : QuizRepository {

    override fun getQuestions(leagues: Set<League>): List<Question> {
        return leagues.flatMap { league ->
            queryByLeague(league)
        }
    }

    private fun queryByLeague(league: League): List<Question> {
        val request = QueryRequest.builder()
            .tableName(properties.teamsTableName)
            .keyConditionExpression("#league = :league")
            .expressionAttributeNames(mapOf("#league" to "league"))
            .expressionAttributeValues(
                mapOf(":league" to AttributeValue.builder().s(league.name).build())
            )
            .build()

        val response = dynamoDbClient.query(request)
        return response.items().map { item ->
            val answers = item["answers"]?.ss()?.toSet() ?: emptySet()
            Question(
                id = item["id"]?.s().orEmpty(),
                league = league,
                logoUrl = item["logoUrl"]?.s().orEmpty(),
                fullName = item["name"]?.s().orEmpty(),
                correctAnswers = answers + (item["name"]?.s().orEmpty())
            )
        }
    }

    override fun saveResult(result: QuizResult) {
        val item = mapOf(
            "userId" to AttributeValue.builder().s(result.userId).build(),
            "completedAtMillis" to AttributeValue.builder().n(result.completedAtMillis.toString()).build(),
            "quizId" to AttributeValue.builder().s(result.quizId).build(),
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

    override fun getResults(): List<QuizResult> {
        val request = QueryRequest.builder()
            .tableName(properties.resultsTableName)
            .keyConditionExpression("#userId = :userId")
            .expressionAttributeNames(mapOf("#userId" to "userId"))
            .expressionAttributeValues(
                mapOf(":userId" to AttributeValue.builder().s(SINGLE_USER_ID).build())
            )
            .scanIndexForward(false)
            .build()

        val response = dynamoDbClient.query(request)
        return response.items().map { item ->
            val leagues = item["leagues"]?.ss()?.mapNotNull { name ->
                runCatching { League.valueOf(name) }.getOrNull()
            }?.toSet() ?: emptySet()
            QuizResult(
                quizId = item["quizId"]?.s().orEmpty(),
                userId = item["userId"]?.s().orEmpty(),
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

    companion object {
        private const val SINGLE_USER_ID = "single-user"
    }
}
