package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.account.AccountAuthentication
import com.elliotmoose.Sports.Quiz.account.AccountAlreadyExistsException
import com.elliotmoose.Sports.Quiz.account.model.Account
import com.elliotmoose.Sports.Quiz.account.properties.AuthenticationProperties
import com.elliotmoose.Sports.Quiz.account.repository.*
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.elliotmoose.Sports.Quiz.common.auth.AuthenticationRequiredException
import com.elliotmoose.Sports.Quiz.common.storage.DynamoTables
import com.elliotmoose.Sports.Quiz.daily.properties.*
import com.elliotmoose.Sports.Quiz.daily.repository.*
import com.elliotmoose.Sports.Quiz.daily.model.*
import com.elliotmoose.Sports.Quiz.quiz.QuizAnswerMatcher
import com.elliotmoose.Sports.Quiz.quiz.properties.QuizDynamoProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.DefaultApplicationArguments
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI
import java.security.SecureRandom
import java.time.*
import com.elliotmoose.Sports.Quiz.monitoring.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@EnabledIfEnvironmentVariable(named = "DYNAMODB_TEST_ENDPOINT", matches = "http://(localhost|127\\.0\\.0\\.1):[0-9]+")
class ProductionPersistenceIntegrationTest {
    @Test
    fun `real DynamoDB preserves auth and attempts across instances with atomic email ownership`() {
        val id = UUID.randomUUID().toString()
        val monitoringProperties = MonitoringProperties(tableName = "monitoring-test-$id")
        val authProperties = AuthenticationProperties(sessionsTableName = "auth-test-$id")
        val attemptProperties = DailyAttemptProperties(tableName = "attempt-test-$id", allowDateOverride = true)
        val scoreProperties = DailyLeaderboardProperties(tableName = "score-test-$id")
        val accountProperties = QuizDynamoProperties(accountsTableName = "account-test-$id")
        val tableNames = listOf(authProperties.sessionsTableName, attemptProperties.tableName, scoreProperties.tableName, accountProperties.accountsTableName, monitoringProperties.tableName)
        DynamoDbClient.builder().endpointOverride(URI.create(System.getenv("DYNAMODB_TEST_ENDPOINT"))).region(Region.EU_WEST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build().use { client ->
                val tables = DynamoTables(client)
                val clock = Clock.fixed(Instant.parse("2099-01-01T00:00:00Z"), ZoneOffset.UTC)
                val mapper = jacksonObjectMapper()
                val sessions = DynamoAuthSessionRepository(client, authProperties, tables)
                val attempts = DynamoDailyAttemptRepository(client, attemptProperties, mapper, tables)
                tables.ensureSingleKeyTable(accountProperties.accountsTableName)
                val monitoring = DynamoMonitoringRepository(client, monitoringProperties, tables)
                monitoring.run(DefaultApplicationArguments())
                sessions.run(DefaultApplicationArguments()); attempts.run(DefaultApplicationArguments())
                DailyScoreTableInitializer(client, scoreProperties).run(DefaultApplicationArguments())
                try {
                    val accounts = DynamoAccountRepository(client, accountProperties)
                    val account = accounts.create(Account(username = "member", email = "member@example.test", emailVerified = true,
                        passwordHash = "hash", nickname = "Member"))
                    assertThat(accounts.findByEmail("MEMBER@EXAMPLE.TEST")).isEqualTo(account)
                    assertThatThrownBy { accounts.create(account.copy(id = UUID.randomUUID().toString())) }
                        .isInstanceOf(AccountAlreadyExistsException::class.java)
                    val token = AccountAuthentication(accounts, clock, SecureRandom(), sessions, authProperties).issue(account.id)
                    val restartedAuth = AccountAuthentication(accounts, clock, SecureRandom(), DynamoAuthSessionRepository(client, authProperties, tables), authProperties)
                    assertThat(restartedAuth.resolve("Bearer $token")!!.id).isEqualTo(account.id)
                    val scores = DailyScoreService(DynamoDailyScoreRepository(client, scoreProperties), scoreProperties, clock)
                    fun service() = DailyService(DailyRepository(mapper), QuizAnswerMatcher(), scores,
                        DynamoDailyAttemptRepository(client, attemptProperties, mapper, tables), attemptProperties, clock, MonitoringService(monitoring, monitoringProperties, clock))
                    val user = UserIdentity(account.id, account.nickname)
                    val started = service().start(user = user)
                    service().nextHint(DailyNextHintRequest(started.sessionId), user)
                    assertThat(service().start(user = user).hintsRevealed).isEqualTo(2)
                    val pool = Executors.newFixedThreadPool(4)
                    try {
                        pool.invokeAll((1..8).map { Callable {
                            service().submitAnswer(DailyAnswerRequest(started.sessionId, "Jordan"), user)
                        } }).forEach { assertThat(it.get().score).isEqualTo(975) }
                    } finally { pool.shutdownNow() }
                    val completed = service().resume(started.sessionId, user)
                    assertThat(completed.completed).isTrue()
                    assertThat(completed.submittedScore!!.score).isEqualTo(975)
                    assertThat(scores.history(user, null, null).scores).hasSize(1)
                    assertThat(monitoring.summary("2099-01-01").totalCompleted).isEqualTo(1)
                    monitoring.error("2099-01-01", "POST", "/v0/daily/answer", 500)
                    assertThat(DynamoMonitoringRepository(client, monitoringProperties, tables).summary("2099-01-01").endpointErrors.single().count).isEqualTo(1)
                    restartedAuth.revoke("Bearer $token")
                    assertThatThrownBy { restartedAuth.resolve("Bearer $token") }.isInstanceOf(AuthenticationRequiredException::class.java)
                } finally { tableNames.forEach { name -> client.deleteTable { it.tableName(name) } } }
            }
    }
}
