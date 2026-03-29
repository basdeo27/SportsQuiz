package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.FaceEntry
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.model.QuizType
import com.elliotmoose.Sports.Quiz.model.TeamEntry
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service


@Service
@ConditionalOnProperty(prefix = "quiz.questions", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalQuizRepository(private val objectMapper: ObjectMapper) : QuestionRepository {

    private val teamsData: Map<League, List<TeamEntry>> by lazy { loadTeamsData() }
    private val facesData: Map<League, List<FaceEntry>> by lazy { loadFacesData() }

    override fun getQuestions(
        leagues: Set<League>,
        type: QuizType,
        difficulty: QuizDifficulty
    ): List<Question> {
        return when (type) {
            QuizType.LOGO -> leagues.flatMap { league ->
                teamsData[league].orEmpty().map { team ->
                    Question(
                        id = team.id,
                        league = league,
                        logoUrl = team.logoUrl,
                        fullName = team.name,
                        hint = team.hint ?: buildHint(team.name),
                        correctAnswers = (team.answers + team.name).toSet()
                    )
                }
            }
            QuizType.FACE -> leagues.flatMap { league ->
                facesData[league].orEmpty()
                    .asSequence()
                    .filter { face -> difficulty == QuizDifficulty.HARD || face.isAllStar }
                    .map { face ->
                        Question(
                            id = face.id,
                            league = league,
                            logoUrl = face.headshotUrl,
                            fullName = face.name,
                            teamId = face.teamId,
                            hint = "Team: ${face.team}",
                            correctAnswers = (face.answers + face.name).toSet()
                        )
                    }
                    .toList()
            }
        }
    }

    private fun loadTeamsData(): Map<League, List<TeamEntry>> {
        return League.entries.associateWith { league ->
            val resource = ClassPathResource("data/logos/${league.name.lowercase()}.json")
            objectMapper.readValue(resource.inputStream, object : TypeReference<List<TeamEntry>>() {})
        }
    }

    private fun loadFacesData(): Map<League, List<FaceEntry>> {
        val supportedLeagues = listOf(League.NBA, League.NHL, League.MLB, League.NFL)
        return supportedLeagues.associateWith { league ->
            val resource = ClassPathResource("data/faces/${league.name.lowercase()}.json")
            objectMapper.readValue(resource.inputStream, object : TypeReference<List<FaceEntry>>() {})
        }
    }

    private fun buildHint(fullName: String): String {
        val tokens = fullName.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) {
            return "No hint available."
        }
        val city = if (tokens.size > 1) tokens.dropLast(1).joinToString(" ") else tokens[0]
        val mascot = tokens.last()
        val mascotInitial = mascot.firstOrNull()?.uppercaseChar() ?: '?'
        return "City: $city • Mascot starts with $mascotInitial"
    }
}
