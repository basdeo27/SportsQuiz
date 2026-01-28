package com.elliotmoose.Sports.Quiz.repository

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import com.elliotmoose.Sports.Quiz.config.QuizDynamoProperties
import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question

@Service
@ConditionalOnProperty(prefix = "quiz.questions", name = ["storage"], havingValue = "dynamo")
class DynamoQuizRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val properties: QuizDynamoProperties
) : QuestionRepository {

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

}
