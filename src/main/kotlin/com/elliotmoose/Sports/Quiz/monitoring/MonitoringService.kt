package com.elliotmoose.Sports.Quiz.monitoring

import com.elliotmoose.Sports.Quiz.common.monitoring.CompletionType
import com.elliotmoose.Sports.Quiz.common.monitoring.UsageMonitor
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Service
@EnableConfigurationProperties(MonitoringProperties::class)
class MonitoringService(private val repository: MonitoringRepository, private val properties: MonitoringProperties, private val clock: Clock) : UsageMonitor {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun quizCompleted(id: String, type: CompletionType, completedAtMillis: Long) {
        try { repository.completed(id, type, date(completedAtMillis), completedAtMillis) }
        catch (ex: Exception) { logger.error("Monitoring completion counter failed: eventId={} type={}", id, type, ex) }
    }
    override fun endpointError(method: String, route: String, status: Int) {
        try { repository.error(date(clock.millis()), method, route, status) }
        catch (ex: Exception) { logger.error("Monitoring endpoint error counter failed: route={} status={}", route, status, ex) }
    }
    fun summary(requestedDate: String?): MonitoringSummary = repository.summary(requestedDate?.let { LocalDate.parse(it).toString() } ?: date(clock.millis()))
    private fun date(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.of(properties.zone)).toLocalDate().toString()
}

@RestController
@RequestMapping("/v0/monitoring")
class MonitoringController(private val service: MonitoringService, private val properties: MonitoringProperties) {
    @GetMapping
    fun summary(@RequestHeader("X-Monitoring-Key", required = false) key: String?,
        @RequestParam(required = false) date: String?): ResponseEntity<MonitoringSummary> {
        if (properties.apiKey.isBlank() || key == null || !MessageDigest.isEqual(properties.apiKey.toByteArray(), key.toByteArray())) {
            return ResponseEntity.notFound().build()
        }
        return try { ResponseEntity.ok(service.summary(date)) } catch (ex: java.time.format.DateTimeParseException) {
            ResponseEntity.badRequest().build()
        }
    }
}
