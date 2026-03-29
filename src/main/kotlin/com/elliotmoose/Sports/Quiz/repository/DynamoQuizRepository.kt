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
import com.elliotmoose.Sports.Quiz.model.QuizType

@Service
@ConditionalOnProperty(prefix = "quiz.questions", name = ["storage"], havingValue = "dynamo")
class DynamoQuizRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : QuestionRepository {

    override fun getQuestions(
        leagues: Set<League>,
        type: QuizType,
        difficulty: QuizDifficulty
    ): List<Question> {
        require(type == QuizType.LOGO) {
            "FACE quizzes are only supported with local question storage."
        }
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
                hint = item["hint"]?.s() ?: buildHint(item["name"]?.s().orEmpty()),
                correctAnswers = answers + (item["name"]?.s().orEmpty())
            )
        }
    }

    private fun buildHint(fullName: String): String {
        val tokens = fullName.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) {
            return "No hint available."
        }
        val city = if (tokens.size > 1) tokens.dropLast(1).joinToString(" ") else tokens[0]
        val mascot = tokens.last()
        val mascotInitial = mascot.firstOrNull()?.uppercaseChar() ?: '?'
        return "City: $city • Mascot starts with $mascotInitial"
    }
}
