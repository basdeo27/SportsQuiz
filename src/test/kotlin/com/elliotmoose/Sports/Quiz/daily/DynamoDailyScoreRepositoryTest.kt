package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.DailyScore
import com.elliotmoose.Sports.Quiz.daily.repository.DailyScoreCursor
import com.elliotmoose.Sports.Quiz.daily.repository.DynamoDailyScoreRepository
import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoDailyScoreRepositoryTest {
    private val client = mock<DynamoDbClient>()
    private val repository = DynamoDailyScoreRepository(client, DailyLeaderboardProperties())
    private val score = DailyScore("USER#one", "2026-05-01", "session", "One", "one", 900, 50, 2, 1000, false)

    @Test
    fun `one conditional write saves canonical score and index keys without separate leaderboard writes`() {
        whenever(client.putItem(any<PutItemRequest>())).thenReturn(PutItemResponse.builder().build())

        repository.saveFirst(score)

        argumentCaptor<PutItemRequest>().apply {
            verify(client).putItem(capture())
            assertThat(firstValue.conditionExpression()).isEqualTo("attribute_not_exists(participantId)")
            assertThat(firstValue.item().getValue("challengeDate").s()).isEqualTo(score.date)
            assertThat(firstValue.item().getValue("rankingKey").s()).isEqualTo(score.rankingKey)
        }
        verifyNoMoreInteractions(client)
    }

    @Test
    fun `daily pagination uses sorted index query and resumes after last returned row rather than lookahead`() {
        val second = score.copy(participantId = "USER#two", score = 800)
        whenever(client.query(any<QueryRequest>())).thenReturn(QueryResponse.builder().items(item(score), item(second)).build())

        val page = repository.leaderboard(score.date, 1, null)
        repository.leaderboard(score.date, 1, page.nextCursor)

        assertThat(page.scores).containsExactly(score)
        val keys = DailyScoreCursor.decode(page.nextCursor, "day:${score.date}", 3)!!
        assertThat(keys).containsExactly(score.participantId, score.date, score.rankingKey)
        argumentCaptor<QueryRequest>().apply {
            verify(client, times(2)).query(capture())
            assertThat(firstValue.indexName()).isEqualTo("daily-ranking")
            assertThat(firstValue.limit()).isEqualTo(2)
            assertThat(firstValue.scanIndexForward()).isTrue()
            assertThat(secondValue.exclusiveStartKey().getValue("rankingKey").s()).isEqualTo(score.rankingKey)
        }
    }

    @Test
    fun `legacy guest-only pages are skipped until members are found without ending pagination early`() {
        val legacyKey = mapOf("participantId" to s("GUEST#old"), "challengeDate" to s(score.date), "rankingKey" to s("0000#old"))
        whenever(client.query(any<QueryRequest>())).thenReturn(
            QueryResponse.builder().items(emptyList<Map<String, AttributeValue>>()).lastEvaluatedKey(legacyKey).build(),
            QueryResponse.builder().items(item(score)).build()
        )

        val page = repository.leaderboard(score.date, 50, null)

        assertThat(page.scores).containsExactly(score)
        assertThat(page.nextCursor).isNull()
        argumentCaptor<QueryRequest>().apply {
            verify(client, times(2)).query(capture())
            assertThat(firstValue.filterExpression()).isEqualTo("attribute_exists(accountId)")
            assertThat(secondValue.exclusiveStartKey()).isEqualTo(legacyKey)
        }
    }

    @Test
    fun `history is strongly consistent and newest first on the base table`() {
        whenever(client.query(any<QueryRequest>())).thenReturn(QueryResponse.builder().items(item(score)).build())

        repository.history(score.participantId, 50, null)

        argumentCaptor<QueryRequest>().apply {
            verify(client).query(capture())
            assertThat(firstValue.indexName()).isNull()
            assertThat(firstValue.consistentRead()).isTrue()
            assertThat(firstValue.scanIndexForward()).isFalse()
            assertThat(firstValue.filterExpression()).isNull()
        }
    }

    @Test
    fun `conditional write conflict returns the persisted first score with a consistent direct read`() {
        whenever(client.putItem(any<PutItemRequest>())).thenThrow(ConditionalCheckFailedException.builder().build())
        whenever(client.getItem(any<GetItemRequest>())).thenReturn(GetItemResponse.builder().item(item(score)).build())

        assertThat(repository.saveFirst(score.copy(score = 1000))).isEqualTo(score)

        argumentCaptor<GetItemRequest>().apply {
            verify(client).getItem(capture())
            assertThat(firstValue.consistentRead()).isTrue()
            assertThat(firstValue.key().keys).containsExactlyInAnyOrder("participantId", "challengeDate")
        }
    }

    private fun item(score: DailyScore): Map<String, AttributeValue> = mapOf(
        "participantId" to s(score.participantId), "challengeDate" to s(score.date), "sessionId" to s(score.sessionId),
        "displayName" to s(score.displayName), "accountId" to s(score.accountId!!), "score" to n(score.score.toLong()),
        "elapsedSeconds" to n(score.elapsedSeconds), "hintsRevealed" to n(score.hintsRevealed.toLong()),
        "completedAtMillis" to n(score.completedAtMillis), "gaveUp" to AttributeValue.builder().bool(score.gaveUp).build(),
        "rankingKey" to s(score.rankingKey)
    )
    private fun s(value: String) = AttributeValue.builder().s(value).build()
    private fun n(value: Long) = AttributeValue.builder().n(value.toString()).build()
}
