package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizDifficulty
import com.elliotmoose.Sports.Quiz.model.QuizType

interface QuestionRepository {
    fun getQuestions(
        leagues: Set<League>,
        type: QuizType,
        difficulty: QuizDifficulty
    ): List<Question>
}
