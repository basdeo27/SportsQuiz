package com.elliotmoose.Sports.Quiz.quiz.repository

import com.elliotmoose.Sports.Quiz.model.FaceTeamOption
import com.elliotmoose.Sports.Quiz.model.HintUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
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
        difficulty: QuizDifficulty,
        teamIds: Set<String>
    ): List<Question> {
        require(type == QuizType.LOGO) {
            "FACE quizzes are only supported with local question storage."
        }
        return leagues.flatMap { league ->
            queryByLeague(league)
        }
    }

    override fun getFaceTeamOptions(leagues: Set<League>): List<FaceTeamOption> {
        return emptyList()
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
                hints = HintUtils.resolveLogoHints(
                    fullName = item["name"]?.s().orEmpty(),
                    hints = readHints(item["hints"]),
                    legacyHint = item["hint"]?.s()
                ),
                correctAnswers = answers + (item["name"]?.s().orEmpty())
            )
        }
    }

    private fun readHints(attribute: AttributeValue?): Map<QuizDifficulty, List<String>> {
        val hintMap = attribute?.m().orEmpty()
        return QuizDifficulty.entries.mapNotNull { difficulty ->
            val values = hintMap[difficulty.name]
                ?.l()
                ?.mapNotNull { it.s() }
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (values.isEmpty()) {
                null
            } else {
                difficulty to values
            }
        }.toMap()
    }
}
