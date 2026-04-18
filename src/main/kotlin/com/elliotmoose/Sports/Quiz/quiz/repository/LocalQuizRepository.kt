package com.elliotmoose.Sports.Quiz.quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.FaceEntry
import com.elliotmoose.Sports.Quiz.model.FaceTeamOption
import com.elliotmoose.Sports.Quiz.model.HintUtils
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
        difficulty: QuizDifficulty,
        teamIds: Set<String>
    ): List<Question> {
        return when (type) {
            QuizType.LOGO -> leagues.flatMap { league ->
                teamsData[league].orEmpty().map { team ->
                    Question(
                        id = team.id,
                        league = league,
                        logoUrl = team.logoUrl,
                        fullName = team.name,
                        hints = HintUtils.resolveLogoHints(team.name, team.hints, team.hint),
                        correctAnswers = (team.answers + team.name).toSet()
                    )
                }
            }
            QuizType.FACE -> {
                val includedLeagues = if (leagues.isEmpty() && teamIds.isNotEmpty()) {
                    facesData.keys
                } else {
                    leagues
                }

                includedLeagues.flatMap { league ->
                facesData[league].orEmpty()
                    .asSequence()
                    .filter { face ->
                        teamIds.isEmpty() || teamIds.contains(face.teamId)
                    }
                    .filter { face ->
                        teamIds.isNotEmpty() ||
                            difficulty == QuizDifficulty.HARD ||
                            face.isAllStar
                    }
                    .map { face ->
                        Question(
                            id = face.id,
                            league = league,
                            logoUrl = face.headshotUrl,
                            fullName = face.name,
                            teamId = face.teamId,
                            hints = HintUtils.buildFaceHints(face.team),
                            correctAnswers = (face.answers + face.name).toSet()
                        )
                    }
                    .toList()
            }
            }
        }
    }

    override fun getFaceTeamOptions(leagues: Set<League>): List<FaceTeamOption> {
        val includedLeagues = if (leagues.isEmpty()) {
            facesData.keys
        } else {
            facesData.keys.intersect(leagues)
        }

        return includedLeagues
            .flatMap { league ->
                facesData[league].orEmpty()
                    .groupBy { face -> face.teamId to face.team }
                    .map { (teamKey, teamFaces) ->
                        FaceTeamOption(
                            teamId = teamKey.first,
                            teamName = teamKey.second,
                            league = league,
                            playerCount = teamFaces.size
                        )
                    }
            }
            .sortedWith(compareBy<FaceTeamOption>({ it.league.name }, { it.teamName }))
    }

    private fun loadTeamsData(): Map<League, List<TeamEntry>> {
        return League.entries.associateWith { league ->
            val resource = ClassPathResource("data/logos/${league.name.lowercase()}.json")
            objectMapper.readValue(resource.inputStream, object : TypeReference<List<TeamEntry>>() {})
        }
    }

    private fun loadFacesData(): Map<League, List<FaceEntry>> {
        val supportedLeagues = listOf(League.NBA, League.NHL, League.MLB, League.NFL, League.EPL)
        return supportedLeagues.associateWith { league ->
            val resource = ClassPathResource("data/faces/${league.name.lowercase()}.json")
            objectMapper.readValue(resource.inputStream, object : TypeReference<List<FaceEntry>>() {})
        }
    }
}
