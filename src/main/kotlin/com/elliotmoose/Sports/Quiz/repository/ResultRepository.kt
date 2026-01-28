package com.elliotmoose.Sports.Quiz.repository

import com.elliotmoose.Sports.Quiz.model.QuizResult

interface ResultRepository {
    fun saveResult(result: QuizResult)
    fun getResults(): List<QuizResult>
}
