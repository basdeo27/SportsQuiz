package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import com.elliotmoose.Sports.Quiz.results.repository.ResultRepository
import org.springframework.stereotype.Service

@Service
class ResultsService(
    private val resultRepository: ResultRepository
) {
    fun getResults(): List<QuizResult> {
        return resultRepository.getResults()
    }

    fun saveResult(result: QuizResult) {
        resultRepository.saveResult(result)
    }
}
