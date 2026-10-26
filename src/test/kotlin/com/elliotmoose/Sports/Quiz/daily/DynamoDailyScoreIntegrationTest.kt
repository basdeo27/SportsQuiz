package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.DailyScore
import com.elliotmoose.Sports.Quiz.daily.properties.DailyLeaderboardProperties
import com.elliotmoose.Sports.Quiz.daily.repository.DailyScoreTableInitializer
import com.elliotmoose.Sports.Quiz.daily.repository.DynamoDailyScoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.DefaultApplicationArguments
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI
import java.util.UUID

/** Opt-in integration test. Only accepts loopback endpoints and uses its own disposable table. */
@EnabledIfEnvironmentVariable(named = "DYNAMODB_TEST_ENDPOINT", matches = "http://(localhost|127\\.0\\.0\\.1):[0-9]+")
class DynamoDailyScoreIntegrationTest {
    @Test
    fun `real DynamoDB persists first scores and pages ranking and member history using the index`() {
        val properties = DailyLeaderboardProperties(tableName = "daily-score-test-${UUID.randomUUID()}")
        DynamoDbClient.builder().endpointOverride(URI.create(System.getenv("DYNAMODB_TEST_ENDPOINT")))
            .region(Region.EU_WEST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .build().use { client ->
                DailyScoreTableInitializer(client, properties).run(DefaultApplicationArguments())
                try {
                    val repository = DynamoDailyScoreRepository(client, properties)
                    val date = "2026-05-01"
                    val records = (0..54).map { index ->
                        DailyScore("USER#$index", date, "session-$index", "Player $index", "$index",
                            index * 10, 20, 1, 1700000000000L + index, false)
                    }
                    records.forEach(repository::saveFirst)
                    // Old anonymous records must be hidden, including when they sort before a full page of members.
                    repeat(55) { index ->
                        repository.saveFirst(DailyScore("GUEST#old-$index", date, "old-$index", "Legacy guest", null,
                            1000, 0, 1, 1700000000000L + index, false))
                    }
                    val member = DailyScore("USER#member", date, "member-session", "Member", "member", 999, 1, 1, 1700000000100, false)
                    repository.saveFirst(member)
                    assertThat(repository.saveFirst(member.copy(score = 1000))).isEqualTo(member)
                    repository.saveFirst(member.copy(date = "2026-05-02", sessionId = "next-day"))

                    // Reconstruct repository to demonstrate reads are persisted, not process-local state.
                    val reloaded = DynamoDailyScoreRepository(client, properties)
                    assertThat(reloaded.findByParticipantAndDate(member.participantId, date)).isEqualTo(member)
                    val history = reloaded.history(member.participantId, 1, null)
                    assertThat(history.scores.single().date).isEqualTo("2026-05-02")
                    val older = reloaded.history(member.participantId, 1, history.nextCursor)
                    assertThat(older.scores).containsExactly(member)
                    assertThat(older.nextCursor).isNull()

                    var actual = emptyList<DailyScore>()
                    // GSI propagation is asynchronous; wait briefly for the expected index contents.
                    for (attempt in 1..50) {
                        val rows = mutableListOf<DailyScore>()
                        var cursor: String? = null
                        do {
                            val page = reloaded.leaderboard(date, 50, cursor)
                            assertThat(page.scores.size).isLessThanOrEqualTo(50)
                            rows.addAll(page.scores)
                            cursor = page.nextCursor
                        } while (cursor != null)
                        actual = rows
                        if (rows.size == 56) break
                        Thread.sleep(100)
                    }
                    assertThat(actual).containsExactlyElementsOf((records + member).sortedBy { it.rankingKey })
                    val dayTwo = reloaded.leaderboard("2026-05-02", 50, null)
                    assertThat(dayTwo.scores.single().sessionId).isEqualTo("next-day")
                } finally {
                    client.deleteTable { it.tableName(properties.tableName) }
                }
            }
    }
}
