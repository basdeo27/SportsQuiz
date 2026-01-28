package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question

interface QuestionRepository {
    fun getQuestions(leagues: Set<League>): List<Question>
}
