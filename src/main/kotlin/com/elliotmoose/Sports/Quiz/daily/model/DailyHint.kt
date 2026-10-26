package com.elliotmoose.Sports.Quiz.daily.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class DailyHintSource {
    REMOTE,
    LOCAL
}

data class DailyStat(
    val name: String,
    val value: String
)

data class DailyStatLine(
    val label: String,
    val stats: List<DailyStat>
)

enum class DailyAccomplishmentKind { TROPHY, ALL_STAR, APPEARANCE, MEDALS }
enum class DailyMedalType { GOLD, SILVER, BRONZE }

data class DailyMedalCount(val medal: DailyMedalType, val count: Int) {
    init { require(count > 0) { "Medal counts must be positive." } }
}

data class DailyAccomplishment(
    val kind: DailyAccomplishmentKind,
    val name: String,
    val count: Int,
    val detail: String? = null,
    val medals: List<DailyMedalCount> = emptyList()
) {
    init {
        require(name.isNotBlank() && count > 0) { "An accomplishment needs a name and positive count." }
        require(medals.map { it.medal }.distinct().size == medals.size) { "Combine duplicate medal types into one count." }
        require(if (kind == DailyAccomplishmentKind.MEDALS) medals.isNotEmpty() && medals.sumOf { it.count } == count else medals.isEmpty()) {
            "Medal accomplishments must include a breakdown matching the total count."
        }
    }
}

data class DailyAccomplishments(
    val entries: List<DailyAccomplishment>,
    val label: String = "Career accomplishments"
) {
    init { require(label.isNotBlank() && entries.isNotEmpty()) { "Accomplishments need a heading and at least one entry." } }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = DailyHint.Image::class, name = "IMAGE"),
    JsonSubTypes.Type(value = DailyHint.Audio::class, name = "AUDIO"),
    JsonSubTypes.Type(value = DailyHint.Video::class, name = "VIDEO"),
    JsonSubTypes.Type(value = DailyHint.Text::class, name = "TEXT"),
    JsonSubTypes.Type(value = DailyHint.StatLine::class, name = "STAT_LINE"),
    JsonSubTypes.Type(value = DailyHint.Accomplishments::class, name = "ACCOMPLISHMENTS"),
)
sealed class DailyHint {
    data class Image(
        val url: String,
        val source: DailyHintSource = DailyHintSource.REMOTE
    ) : DailyHint()

    data class Audio(
        val url: String,
        val source: DailyHintSource = DailyHintSource.REMOTE
    ) : DailyHint()

    data class Video(
        val url: String,
        val source: DailyHintSource = DailyHintSource.REMOTE
    ) : DailyHint()

    data class Text(
        val value: String,
        val attribution: String? = null
    ) : DailyHint()

    data class StatLine(
        val statLine: DailyStatLine
    ) : DailyHint()

    data class Accomplishments(
        val accomplishments: DailyAccomplishments
    ) : DailyHint()
}
