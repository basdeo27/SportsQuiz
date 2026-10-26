package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.DailyEntry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.Random

@Component
class DailyRepository(private val objectMapper: ObjectMapper) {

    private val catalog: List<DailyEntry> by lazy {
        PathMatchingResourcePatternResolver().getResources("classpath*:data/daily/*.json")
            .flatMap { resource ->
                resource.inputStream.use { objectMapper.readValue<List<DailyEntry>>(it) }
            }.sortedBy { it.date }
    }

    fun findForLiveDate(date: LocalDate): DailyEntry? {
        findByDate(date)?.let { return it }
        if (catalog.isEmpty()) return null
        // A shared date seed keeps the pick identical across requests and instances.
        val selected = catalog[Random(date.toEpochDay()).nextInt(catalog.size)]
        return selected.copy(date = date.toString())
    }

    fun findByDate(date: LocalDate): DailyEntry? {
        val monthKey = date.toString().substring(0, 7) // "YYYY-MM"
        val resource = ClassPathResource("data/daily/$monthKey.json")
        if (!resource.exists()) return null
        val entries: List<DailyEntry> = resource.inputStream.use { objectMapper.readValue(it) }
        return entries.find { it.date == date.toString() }
    }

    fun findAllDates(): List<String> {
        return catalog.map { it.date }
    }
}
