package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.League
import com.elliotmoose.Sports.Quiz.model.Question
import com.elliotmoose.Sports.Quiz.model.QuizResult

interface QuizRepository {
    fun getQuestions(leagues: Set<League>): List<Question>
    fun saveResult(result: QuizResult)
    fun getResults(): List<QuizResult>
}
