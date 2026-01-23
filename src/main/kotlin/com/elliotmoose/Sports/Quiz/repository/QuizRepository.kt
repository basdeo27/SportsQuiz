package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question

interface QuizRepository {
    fun getQuestions(leagues: Set<League>): List<Question>
}
