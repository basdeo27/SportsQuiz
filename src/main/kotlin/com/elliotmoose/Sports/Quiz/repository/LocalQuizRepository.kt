package com.elliotmoose.Sports.Quiz.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.TeamEntry

@Service
@ConditionalOnProperty(prefix = "quiz.dynamo", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LocalQuizRepository(private val objectMapper: ObjectMapper) : QuizRepository {

    private val teamsData: Map<League, List<TeamEntry>> by lazy { loadTeamsData() }

    override fun getQuestions(leagues: Set<League>): List<Question> {
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
