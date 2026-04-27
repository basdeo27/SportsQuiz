package com.elliotmoose.Sports.Quiz.results.repository

import com.elliotmoose.Sports.Quiz.results.model.QuizResult

interface ResultRepository {
    fun saveResult(result: QuizResult)
    fun getResults(userId: String, limit: Int, before: Long?): List<QuizResult>
}
