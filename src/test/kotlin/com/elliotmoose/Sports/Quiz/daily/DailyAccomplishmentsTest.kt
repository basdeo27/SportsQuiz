package com.elliotmoose.Sports.Quiz.daily

import com.elliotmoose.Sports.Quiz.daily.model.*
import com.elliotmoose.Sports.Quiz.common.auth.UserIdentity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate

class DailyAccomplishmentsTest {
    private val mapper = jacksonObjectMapper()
    private val repository = DailyRepository(mapper)

    @Test
    fun `today's challenge has ordered clues and local image files with no answer in their URLs`() {
        val entry = repository.findByDate(LocalDate.of(2026, 10, 24))!!

        assertThat(entry.answer).isEqualTo("Markus Näslund")
        assertThat(entry.answers).contains("Markus Naslund", "Naslund", "Näslund")
        assertThat(entry.hints).hasSize(5)
        assertThat((entry.hints[0] as DailyHint.Text).attribution).contains("West Coast Express")
        assertThat((entry.hints[1] as DailyHint.StatLine).statLine.stats.map { it.value }).containsExactly("48", "56", "104")
        for (hint in entry.hints.subList(2, 4).map { it as DailyHint.Image }) {
            assertThat(hint.source).isEqualTo(DailyHintSource.LOCAL)
            assertThat(hint.url).doesNotContainIgnoringCase("naslund")
            assertThat(ClassPathResource("static/${hint.url}").exists()).isTrue()
        }
        val honours = (entry.hints[4] as DailyHint.Accomplishments).accomplishments
        assertThat(honours.entries.map { it.count }).containsExactly(1, 5, 2, 3)
        assertThat(honours.entries.last().medals).containsExactly(
            DailyMedalCount(DailyMedalType.SILVER, 1), DailyMedalCount(DailyMedalType.BRONZE, 2))
    }

    @Test
    fun `new quote and accomplishments hints survive persisted attempt round trips`() {
        val entry = repository.findByDate(LocalDate.of(2026, 10, 24))!!
        val attempt = DailySession(date = entry.date, entry = entry, user = UserIdentity("test", "Player"))

        val restored = mapper.readValue(mapper.writeValueAsString(attempt), DailySession::class.java)

        assertThat(restored).isEqualTo(attempt)
        assertThat(restored.entry.hints.last()).isInstanceOf(DailyHint.Accomplishments::class.java)
    }

    @Test
    fun `older text and stat hints remain compatible`() {
        val entry = repository.findByDate(LocalDate.of(2026, 5, 3))!!

        assertThat(entry.hints).hasSize(5)
        assertThat((entry.hints.first() as DailyHint.Text).attribution).isNull()
        assertThat(entry.hints[1]).isInstanceOf(DailyHint.StatLine::class.java)
    }

    @Test
    fun `medal totals cannot disagree with their breakdown`() {
        assertThatThrownBy { DailyAccomplishment(DailyAccomplishmentKind.MEDALS, "World medals", 3,
            medals = listOf(DailyMedalCount(DailyMedalType.SILVER, 1))) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DailyMedalCount(DailyMedalType.GOLD, 0) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
