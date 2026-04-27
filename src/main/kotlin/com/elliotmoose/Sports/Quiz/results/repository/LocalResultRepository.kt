package com.elliotmoose.Sports.Quiz.results.repository

import com.elliotmoose.Sports.Quiz.results.model.QuizResult
import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "quiz", name = ["storage"], havingValue = "local", matchIfMissing = true)
class LocalResultRepository : ResultRepository {

    private val results = ConcurrentHashMap<String, QuizResult>()

    override fun saveResult(result: QuizResult) {
        results[result.quizId] = result
    }

    override fun getResults(userId: String, limit: Int, before: Long?): List<QuizResult> {
        return results.values
            .filter { it.userId == userId }
            .filter { before == null || it.completedAtMillis < before }
            .sortedByDescending { it.completedAtMillis }
            .take(limit)
    }
}
