package com.elliotmoose.Sports.Quiz.results.repository

import com.elliotmoose.Sports.Quiz.results.model.QuizResult

interface ResultRepository {
    fun saveResult(result: QuizResult)
    fun getResults(): List<QuizResult>
}
