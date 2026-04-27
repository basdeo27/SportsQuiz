package com.elliotmoose.Sports.Quiz.results

import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import com.elliotmoose.Sports.Quiz.results.model.QuizResultsResponse
import com.elliotmoose.Sports.Quiz.results.repository.ResultRepository
import org.springframework.stereotype.Service

@Service
class ResultsService(
    private val resultRepository: ResultRepository
) {
    fun getResults(userId: String, limit: Int, before: Long?): QuizResultsResponse {
        val fetched = resultRepository.getResults(userId, limit + 1, before)
        val hasMore = fetched.size > limit
        return QuizResultsResponse(results = fetched.take(limit), hasMore = hasMore)
    }

    fun saveResult(result: QuizResult) {
        resultRepository.saveResult(result)
    }
}
