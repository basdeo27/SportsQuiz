package com.elliotmoose.Sports.Quiz.daily.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("quiz.daily.leaderboard")
data class DailyLeaderboardProperties(
    val tableName: String = "sports-quiz-daily-scores",
    val pageSize: Int = 50,
    val maxPageSize: Int = 100,
    val zone: String = "Europe/London"
) {
    init {
        require(pageSize in 1..maxPageSize) { "Daily leaderboard page-size must be between 1 and max-page-size." }
        java.time.ZoneId.of(zone)
    }
}
