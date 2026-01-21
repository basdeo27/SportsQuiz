package com.elliotmoose.Sports.Quiz.quiz

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class QuizRepository(private val objectMapper: ObjectMapper) {

    private val teamsData: Map<League, List<TeamEntry>> by lazy { loadTeamsData() }

    fun getQuestions(leagues: Set<League>): List<Question> {
        return leagues.flatMap { league ->
            teamsData[league].orEmpty().map { team ->
                Question(
                    id = team.id,
                    league = league,
                    logoUrl = team.logoUrl,
                    fullName = team.name,
                    correctAnswers = (team.answers + team.name).toSet()
                )
            }
        }
    }

    private fun loadTeamsData(): Map<League, List<TeamEntry>> {
        return League.entries.associateWith { league ->
            val resource = ClassPathResource("data/${league.name.lowercase()}.json")
            objectMapper.readValue(resource.inputStream, object : TypeReference<List<TeamEntry>>() {})
        }
    }
}

data class TeamEntry(
    val id: String,
    val name: String,
    val logoUrl: String,
    val answers: List<String> = emptyList()
)
