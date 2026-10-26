package com.elliotmoose.Sports.Quiz.monitoring

import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@ConfigurationProperties("quiz.monitoring")
data class MonitoringProperties(val apiKey: String = "", val tableName: String = "sports-quiz-monitoring", val zone: String = "Europe/London", val allowDevelopmentKey: Boolean = false) {
    init {
        require(apiKey.isBlank() || allowDevelopmentKey || apiKey.length >= 32) { "MONITORING_API_KEY must contain at least 32 characters." }
        java.time.ZoneId.of(zone)
    }
}
data class MonitoringSummary(val date: String, val trackingSinceMillis: Long?, val totalCompleted: Long,
    val completedByType: Map<String, Long>, val completedOnDate: Map<String, Long>, val endpointErrors: List<EndpointErrorCount>)
data class EndpointErrorCount(val method: String, val endpoint: String, val status: Int, val count: Long)

interface MonitoringRepository {
    fun completed(id: String, type: CompletionType, date: String, at: Long)
    fun error(date: String, method: String, route: String, status: Int)
    fun summary(date: String): MonitoringSummary
}

@Repository
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalMonitoringRepository : MonitoringRepository {
    private val events = mutableSetOf<String>()
    private val totals = mutableMapOf<String, Long>()
    private val days = mutableMapOf<String, MutableMap<String, Long>>()
    private val errors = ConcurrentHashMap<String, MutableMap<String, Long>>()
    private var since: Long? = null
    @Synchronized override fun completed(id: String, type: CompletionType, date: String, at: Long) {
        if (!events.add(id)) return
        if (since == null) since = at
        totals[type.name] = (totals[type.name] ?: 0) + 1
        val day = days.getOrPut(date) { mutableMapOf() }
        day[type.name] = (day[type.name] ?: 0) + 1
    }
    @Synchronized override fun error(date: String, method: String, route: String, status: Int) {
        val day = errors.getOrPut(date) { mutableMapOf() }
        val key = "$method|$route|$status"
        day[key] = (day[key] ?: 0) + 1
    }
    @Synchronized override fun summary(date: String) = MonitoringSummary(date, since, totals.values.sum(), CompletionType.entries.associate { it.name to (totals[it.name] ?: 0) }, CompletionType.entries.associate { it.name to (days[date]?.get(it.name) ?: 0) },
        errors[date].orEmpty().map { (key, count) -> val parts = key.split('|'); EndpointErrorCount(parts[0], parts[1], parts[2].toInt(), count) }.sortedByDescending { it.count })
}
