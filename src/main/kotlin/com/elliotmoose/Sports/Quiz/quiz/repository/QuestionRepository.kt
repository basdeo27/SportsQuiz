package com.elliotmoose.Sports.Quiz.quiz.repository

import com.elliotmoose.Sports.Quiz.model.FaceTeamOption
import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.model.QuizType

interface QuestionRepository {
    fun getQuestions(
        leagues: Set<League>,
        type: QuizType,
        difficulty: QuizDifficulty,
        teamIds: Set<String> = emptySet()
    ): List<Question>

    fun getFaceTeamOptions(leagues: Set<League> = emptySet()): List<FaceTeamOption>
}
